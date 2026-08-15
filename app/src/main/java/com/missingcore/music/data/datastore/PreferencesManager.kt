package com.missingcore.music.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.missingcore.music.data.api.AudiusMusicProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "musync_settings")

class PreferencesManager(private val context: Context) {

    private val securePrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "musync_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("musync_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://musync-production-2fc5.up.railway.app"
        val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val KEY_HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        private const val SECURE_KEY_API_KEY = "secure_api_key"
    }

    val equalizerPreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_EQUALIZER_PRESET] ?: "Bass Boost"
    }

    fun getEqualizerPreset(): String {
        return securePrefs.getString("saved_eq_preset", "Bass Boost") ?: "Bass Boost"
    }

    suspend fun saveEqualizerPreset(preset: String) {
        securePrefs.edit().putString("saved_eq_preset", preset).apply()
        context.dataStore.edit { prefs ->
            prefs[KEY_EQUALIZER_PRESET] = preset
        }
    }

    val providerId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROVIDER_ID] ?: "universal"
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val audioQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_QUALITY] ?: "high"
    }

    val hapticIntensity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_INTENSITY] ?: "BALANCED"
    }

    val isShuffle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHUFFLE] ?: false
    }

    val repeatMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPEAT_MODE] ?: "OFF"
    }

    fun getApiKey(): String? {
        return securePrefs.getString(SECURE_KEY_API_KEY, null)
    }

    fun getBaseUrl(): String {
        return securePrefs.getString("saved_base_url", null) ?: DEFAULT_BASE_URL
    }

    fun setApiKey(apiKey: String?) {
        securePrefs.edit().apply {
            if (apiKey.isNullOrBlank()) {
                remove(SECURE_KEY_API_KEY)
            } else {
                putString(SECURE_KEY_API_KEY, apiKey)
            }
            apply()
        }
    }

    suspend fun setProviderId(providerId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = providerId
        }
    }

    suspend fun setBaseUrl(baseUrl: String) {
        securePrefs.edit().putString("saved_base_url", baseUrl).apply()
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl
        }
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_QUALITY] = quality
        }
    }

    suspend fun setHapticIntensity(intensity: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTIC_INTENSITY] = intensity
        }
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHUFFLE] = enabled
        }
    }

    suspend fun setRepeatMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPEAT_MODE] = mode
        }
    }
}
