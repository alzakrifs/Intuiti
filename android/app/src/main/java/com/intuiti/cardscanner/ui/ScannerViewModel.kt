package com.intuiti.cardscanner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.intuiti.cardscanner.CardScannerApplication
import com.intuiti.cardscanner.data.CardExtractor
import com.intuiti.cardscanner.data.ContactFields
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
    ) : ScanPhase
    data class Error(val message: String) : ScanPhase
}

data class SettingsUiState(
    val apiKey: String = "",
    val message: String? = null,
    val isError: Boolean = false,
)

class ScannerViewModel(
    private val application: Application,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val extractor = CardExtractor(application)

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    val apiKey: StateFlow<String> = settings.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    fun onImageCaptured(uri: Uri) {
        viewModelScope.launch {
            val key = apiKey.value
            _phase.value = ScanPhase.Working(
                if (key.isNotBlank()) "Asking Claude to read the card…" else "Reading text on device…",
            )
            try {
                val result = extractor.extract(uri, key)
                _phase.value = ScanPhase.Review(
                    imageUri = uri,
                    fields = result.fields,
                    source = result.source,
                )
            } catch (t: Throwable) {
                _phase.value = ScanPhase.Error(t.message ?: "Could not read the card.")
            }
        }
    }

    fun updateField(transform: (ContactFields) -> ContactFields) {
        val current = _phase.value as? ScanPhase.Review ?: return
        _phase.value = current.copy(fields = transform(current.fields))
    }

    fun reset() {
        _phase.value = ScanPhase.Idle
    }

    fun onSettingsKeyChanged(value: String) {
        _settingsState.update { it.copy(apiKey = value, message = null, isError = false) }
    }

    fun loadSettings() {
        _settingsState.update { it.copy(apiKey = apiKey.value, message = null, isError = false) }
    }

    fun saveApiKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            _settingsState.update { it.copy(message = "Enter a key first.", isError = true) }
            return
        }
        if (!trimmed.startsWith("sk-ant-")) {
            _settingsState.update {
                it.copy(message = "That does not look like an Anthropic key.", isError = true)
            }
            return
        }
        viewModelScope.launch {
            settings.setApiKey(trimmed)
            _settingsState.update {
                it.copy(apiKey = trimmed, message = "Saved. AI mode is on.", isError = false)
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            settings.clearApiKey()
            _settingsState.update {
                SettingsUiState(apiKey = "", message = "Cleared. Using on-device OCR.", isError = false)
            }
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
