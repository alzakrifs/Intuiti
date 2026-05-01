package com.intuiti.cardscanner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val apiKey: Flow<String> = store.data.map { it[API_KEY] ?: "" }

    suspend fun setApiKey(value: String) {
        store.edit { prefs ->
            if (value.isBlank()) prefs.remove(API_KEY) else prefs[API_KEY] = value
        }
    }

    suspend fun clearApiKey() {
        store.edit { prefs -> prefs.remove(API_KEY) }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("anthropic_api_key")
    }
}
