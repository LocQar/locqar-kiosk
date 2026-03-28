package com.locqar.kiosk.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kiosk_settings")

/**
 * Persistent kiosk settings stored via Jetpack DataStore.
 * These survive app restarts and don't require rebuilding the APK.
 */
class KioskSettings(private val context: Context) {

    companion object {
        val KEY_LOCKER_SN = stringPreferencesKey("locker_sn")
        val KEY_STATION_NUMBER = intPreferencesKey("station_number")
        val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
        val KEY_POLL_INTERVAL_MS = longPreferencesKey("poll_interval_ms")
        val KEY_API_BASE_URL = stringPreferencesKey("api_base_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")

        const val DEFAULT_LOCKER_SN = ""
        const val DEFAULT_STATION_NUMBER = 1
        const val DEFAULT_DEMO_MODE = true
        const val DEFAULT_POLL_INTERVAL_MS = 5000L
    }

    val lockerSN: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCKER_SN] ?: DEFAULT_LOCKER_SN
    }

    val stationNumber: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_STATION_NUMBER] ?: DEFAULT_STATION_NUMBER
    }

    val demoMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEMO_MODE] ?: DEFAULT_DEMO_MODE
    }

    val pollIntervalMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_POLL_INTERVAL_MS] ?: DEFAULT_POLL_INTERVAL_MS
    }

    val apiBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_BASE_URL] ?: ""
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: ""
    }

    suspend fun setLockerSN(sn: String) {
        context.dataStore.edit { it[KEY_LOCKER_SN] = sn }
    }

    suspend fun setStationNumber(station: Int) {
        context.dataStore.edit { it[KEY_STATION_NUMBER] = station }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEMO_MODE] = enabled }
    }

    suspend fun setPollIntervalMs(ms: Long) {
        context.dataStore.edit { it[KEY_POLL_INTERVAL_MS] = ms }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_API_BASE_URL] = url }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    /**
     * Returns true if the kiosk has been provisioned (has a locker SN set).
     */
    val isProvisioned: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[KEY_LOCKER_SN].isNullOrBlank()
    }
}
