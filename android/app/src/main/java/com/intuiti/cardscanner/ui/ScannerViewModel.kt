package com.intuiti.cardscanner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.intuiti.cardscanner.CardScannerApplication
import com.intuiti.cardscanner.data.AICoreExtractor
import com.intuiti.cardscanner.data.CardExtractor
import com.intuiti.cardscanner.data.ContactFields
import com.intuiti.cardscanner.data.EnginePreference
import com.intuiti.cardscanner.data.ExtractionSource
import com.intuiti.cardscanner.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data class Working(val message: String) : ScanPhase
    data class Review(
        val imageUri: Uri,
        val fields: ContactFields,
        val source: ExtractionSource,
        val errorMessage: String? = null,
    ) : ScanPhase
    data class Error(val message: String) : ScanPhase
}

data class SettingsUiState(
    val apiKey: String = "",
    val enginePreference: EnginePreference = EnginePreference.Auto,
    val aiCoreAvailability: AICoreExtractor.Availability = AICoreExtractor.Availability.Unknown,
    val message: String? = null,
    val isError: Boolean = false,
)

class ScannerViewModel(
    application: Application,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val extractor = CardExtractor(application)

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    val apiKey: StateFlow<String> = settings.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val enginePreference: StateFlow<EnginePreference> = settings.enginePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, EnginePreference.Auto)

    private val _aiCoreAvailability =
        MutableStateFlow<AICoreExtractor.Availability>(AICoreExtractor.Availability.Unknown)
    val aiCoreAvailability: StateFlow<AICoreExtractor.Availability> = _aiCoreAvailability.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    init {
        // Run the AICore availability check eagerly at app launch.
        viewModelScope.launch {
            val state = extractor.aiCore.checkAvailability()
            _aiCoreAvailability.value = state
            _settingsState.update { it.copy(aiCoreAvailability = state) }
        }
    }

    fun onImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val key = apiKey.value
            val pref = enginePreference.value
            val aiCoreOk = aiCoreAvailability.value is AICoreExtractor.Availability.AvailableTextOnly
            _phase.value = ScanPhase.Working(workingMessage(pref, key, aiCoreOk))
            try {
                val result = extractor.extract(
                    imageUri = uri,
                    preference = pref,
                    apiKey = key,
                    aiCoreAvailable = aiCoreOk,
                )
                _phase.value = ScanPhase.Review(
                    imageUri = uri,
                    fields = result.fields,
                    source = result.source,
                    errorMessage = result.errorMessage,
                )
            } catch (t: Throwable) {
                _phase.value = ScanPhase.Error(t.message ?: "Could not read the card.")
            }
        }
    }

    private fun workingMessage(pref: EnginePreference, key: String, aiCoreOk: Boolean): String =
        when (pref) {
            EnginePreference.Claude -> if (key.isNotBlank()) "Asking Claude to read the card…" else "Reading text on device…"
            EnginePreference.AICore -> if (aiCoreOk) "Running on-device AI…" else "Reading text on device…"
            EnginePreference.Tesseract -> "Reading text on device…"
            EnginePreference.Auto -> if (aiCoreOk) "Running on-device AI…" else "Reading text on device…"
        }

    fun updateField(transform: (ContactFields) -> ContactFields) {
        val current = _phase.value as? ScanPhase.Review ?: return
        _phase.value = current.copy(fields = transform(current.fields))
    }

    fun reset() {
        _phase.value = ScanPhase.Idle
    }

    fun loadSettings() {
        _settingsState.update {
            it.copy(
                apiKey = apiKey.value,
                enginePreference = enginePreference.value,
                aiCoreAvailability = aiCoreAvailability.value,
                message = null,
                isError = false,
            )
        }
    }

    fun onSettingsKeyChanged(value: String) {
        _settingsState.update { it.copy(apiKey = value, message = null, isError = false) }
    }

    fun setEnginePreference(preference: EnginePreference) {
        viewModelScope.launch {
            settings.setEnginePreference(preference)
            _settingsState.update { it.copy(enginePreference = preference, message = null, isError = false) }
        }
    }

    fun saveApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            _settingsState.update { it.copy(message = "Enter a key first.", isError = true) }
            return
        }
        viewModelScope.launch {
            settings.setApiKey(trimmed)
            val warning = if (!trimmed.startsWith("sk-ant-")) {
                " Heads-up: most Anthropic keys start with sk-ant-."
            } else {
                ""
            }
            _settingsState.update {
                it.copy(
                    apiKey = trimmed,
                    message = "Saved.$warning",
                    isError = false,
                )
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settings.clearApiKey()
            _settingsState.update { it.copy(apiKey = "", message = "Cleared.", isError = false) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as CardScannerApplication
                @Suppress("UNCHECKED_CAST")
                return ScannerViewModel(app, app.settings) as T
            }
        }
    }
}
