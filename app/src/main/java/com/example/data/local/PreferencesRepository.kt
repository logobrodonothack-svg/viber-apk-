package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clipvault_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wifiOnly: Boolean = false,
    val autoCheckClipboard: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_CHECK_CLIPBOARD = booleanPreferencesKey("auto_check_clipboard")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
        UserPreferences(
            themeMode = themeMode,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            autoCheckClipboard = prefs[Keys.AUTO_CHECK_CLIPBOARD] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setAutoCheckClipboard(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_CLIPBOARD] = enabled }
    }
}
