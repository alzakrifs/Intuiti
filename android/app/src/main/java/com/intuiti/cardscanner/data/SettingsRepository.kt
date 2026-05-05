package com.intuiti.cardscanner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Which engine the user has chosen for extraction. The router resolves [Auto]
 * to the best available concrete engine at call time.
 */
enum class EnginePreference {
    Auto,
    Tesseract,
    AICore,
    Claude,
    ;

    val storageKey: String get() = name

    companion object {
        fun fromStorage(value: String?): EnginePreference =
            entries.firstOrNull { it.storageKey == value } ?: Auto
    }
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val apiKey: Flow<String> = store.data.map { it[API_KEY] ?: "" }

    val enginePreference: Flow<EnginePreference> = store.data.map {
        EnginePreference.fromStorage(it[ENGINE_PREF])
    }

    suspend fun setApiKey(value: String) {
        store.edit { prefs ->
            if (value.isBlank()) prefs.remove(API_KEY) else prefs[API_KEY] = value
        }
    }

    suspend fun clearApiKey() {
        store.edit { prefs -> prefs.remove(API_KEY) }
    }

    suspend fun setEnginePreference(preference: EnginePreference) {
        store.edit { prefs -> prefs[ENGINE_PREF] = preference.storageKey }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("anthropic_api_key")
        val ENGINE_PREF = stringPreferencesKey("engine_preference")
    }
}
