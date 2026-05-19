package com.ceo3.docs.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        
        val THEME = stringPreferencesKey("theme")
        
        val REQUIRE_PASSCODE = booleanPreferencesKey("require_passcode")
        val USE_BIOMETRICS = booleanPreferencesKey("use_biometrics")
        val PASSCODE = stringPreferencesKey("passcode")
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: "Alex Mercer"
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[USER_NAME] = name }
    }

    val userEmailFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL] ?: "alex.mercer@docs.app"
    }

    suspend fun setUserEmail(email: String) {
        context.dataStore.edit { preferences -> preferences[USER_EMAIL] = email }
    }

    val autoSyncFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_SYNC] ?: true
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_SYNC] = enabled }
    }

    val syncWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SYNC_WIFI_ONLY] ?: true
    }

    suspend fun setSyncWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SYNC_WIFI_ONLY] = enabled }
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME] ?: "System Default"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences -> preferences[THEME] = theme }
    }

    val requirePasscodeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REQUIRE_PASSCODE] ?: false
    }

    suspend fun setRequirePasscode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[REQUIRE_PASSCODE] = enabled }
    }

    val useBiometricsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_BIOMETRICS] ?: false
    }

    suspend fun setUseBiometrics(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[USE_BIOMETRICS] = enabled }
    }

    val passcodeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PASSCODE] ?: ""
    }

    suspend fun setPasscode(passcode: String) {
        context.dataStore.edit { preferences -> preferences[PASSCODE] = passcode }
    }
}
