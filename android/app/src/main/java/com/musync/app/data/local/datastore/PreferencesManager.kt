package com.musync.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
        private const val SECURE_KEY_API_KEY = "secure_api_key"

        // General
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_REGION = stringPreferencesKey("region")
        val KEY_MUSIC_LANGUAGES = stringSetPreferencesKey("preferred_music_languages")
        val KEY_ALLOW_EXPLICIT = booleanPreferencesKey("allow_explicit_content")
        val KEY_LANDING_PAGE = stringPreferencesKey("default_landing_page")
        val KEY_DEFAULT_PLAYBACK_BEHAVIOR = stringPreferencesKey("default_playback_behavior")

        // Playback
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_enabled")
        val KEY_INTELLIGENT_SHUFFLE = booleanPreferencesKey("intelligent_shuffle_enabled")
        val KEY_GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback_enabled")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val KEY_CONTINUE_PLAYING = booleanPreferencesKey("continue_playing_enabled")
        val KEY_QUEUE_BEHAVIOR = stringPreferencesKey("queue_behavior")
        val KEY_RECORD_HISTORY = booleanPreferencesKey("record_listening_history")
        val KEY_LAST_TRACK_ID = stringPreferencesKey("last_played_track_id")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_played_position_ms")

        // Audio
        val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val KEY_DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val KEY_HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val KEY_DOLBY_ATMOS_MODE = stringPreferencesKey("dolby_atmos_mode")
        val KEY_AUDIO_NORMALIZATION = booleanPreferencesKey("audio_normalization_enabled")

        // Discovery
        val KEY_PERSONALIZED_RECOMMENDATIONS = booleanPreferencesKey("personalized_recommendations")
        val KEY_TRENDING_REGION = stringPreferencesKey("trending_region")
        val KEY_NEW_RELEASE_LANGUAGE = stringPreferencesKey("new_release_language")
        val KEY_PERSONALIZATION_LEVEL = stringPreferencesKey("personalization_level")
        val KEY_TRENDING_ENABLED = booleanPreferencesKey("trending_enabled")
        val KEY_NEW_RELEASES_ENABLED = booleanPreferencesKey("new_releases_enabled")
        val KEY_DISCOVERY_ENABLED = booleanPreferencesKey("discovery_enabled")
        val KEY_PREFERRED_ARTISTS = stringSetPreferencesKey("preferred_artists")
        val KEY_FAVORITE_GENRES = stringSetPreferencesKey("favorite_genres")

        // Notifications
        val KEY_NOTIF_NEW_RELEASES = booleanPreferencesKey("notif_new_releases")
        val KEY_NOTIF_TRENDING = booleanPreferencesKey("notif_trending")
        val KEY_NOTIF_RECOMMENDATIONS = booleanPreferencesKey("notif_recommendations")
        val KEY_NOTIF_PLAYLIST = booleanPreferencesKey("notif_playlist")
        val KEY_NOTIF_SYSTEM = booleanPreferencesKey("notif_system")

        // Appearance
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val KEY_INTERFACE_EFFECTS = stringPreferencesKey("interface_effects")

        // Storage & Data
        val KEY_NETWORK_USAGE = stringPreferencesKey("network_usage")
    }

    // ──────────────────────────────────────────────────────────────
    // GENERAL PREFERENCES FLOWS & GETTERS / SETTERS
    // ──────────────────────────────────────────────────────────────

    val appLanguage: Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: "English" }
    fun getAppLanguage(): String = securePrefs.getString("saved_app_lang", "English") ?: "English"
    suspend fun setAppLanguage(lang: String) {
        securePrefs.edit().putString("saved_app_lang", lang).apply()
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = lang }
    }

    val region: Flow<String> = context.dataStore.data.map { it[KEY_REGION] ?: "India" }
    fun getRegion(): String = securePrefs.getString("saved_region", "India") ?: "India"
    suspend fun setRegion(region: String) {
        securePrefs.edit().putString("saved_region", region).apply()
        context.dataStore.edit { it[KEY_REGION] = region }
    }

    val musicLanguages: Flow<Set<String>> = context.dataStore.data.map {
        it[KEY_MUSIC_LANGUAGES] ?: setOf("Telugu", "Hindi", "English")
    }
    fun getMusicLanguages(): Set<String> =
        securePrefs.getStringSet("saved_music_langs", null) ?: setOf("Telugu", "Hindi", "English")
    suspend fun setMusicLanguages(languages: Set<String>) {
        securePrefs.edit().putStringSet("saved_music_langs", languages).apply()
        context.dataStore.edit { it[KEY_MUSIC_LANGUAGES] = languages }
    }

    val allowExplicitContent: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_EXPLICIT] ?: true }
    fun getAllowExplicitContent(): Boolean = securePrefs.getBoolean("saved_allow_explicit", true)
    suspend fun setAllowExplicitContent(allow: Boolean) {
        securePrefs.edit().putBoolean("saved_allow_explicit", allow).apply()
        context.dataStore.edit { it[KEY_ALLOW_EXPLICIT] = allow }
    }

    val defaultLandingPage: Flow<String> = context.dataStore.data.map { it[KEY_LANDING_PAGE] ?: "Home" }
    fun getDefaultLandingPage(): String = securePrefs.getString("saved_landing_page", "Home") ?: "Home"
    suspend fun setDefaultLandingPage(page: String) {
        securePrefs.edit().putString("saved_landing_page", page).apply()
        context.dataStore.edit { it[KEY_LANDING_PAGE] = page }
    }

    val defaultPlaybackBehavior: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_PLAYBACK_BEHAVIOR] ?: "Resume" }
    fun getDefaultPlaybackBehavior(): String = securePrefs.getString("saved_playback_behavior", "Resume") ?: "Resume"
    suspend fun setDefaultPlaybackBehavior(behavior: String) {
        securePrefs.edit().putString("saved_playback_behavior", behavior).apply()
        context.dataStore.edit { it[KEY_DEFAULT_PLAYBACK_BEHAVIOR] = behavior }
    }

    // ──────────────────────────────────────────────────────────────
    // PLAYBACK PREFERENCES FLOWS & SETTERS
    // ──────────────────────────────────────────────────────────────

    val autoplay: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTOPLAY] ?: true }
    fun getAutoplay(): Boolean = securePrefs.getBoolean("saved_autoplay", true)
    suspend fun setAutoplay(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_autoplay", enabled).apply()
        context.dataStore.edit { it[KEY_AUTOPLAY] = enabled }
    }

    val intelligentShuffle: Flow<Boolean> = context.dataStore.data.map { it[KEY_INTELLIGENT_SHUFFLE] ?: true }
    fun getIntelligentShuffle(): Boolean = securePrefs.getBoolean("saved_intelligent_shuffle", true)
    suspend fun setIntelligentShuffle(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_intelligent_shuffle", enabled).apply()
        context.dataStore.edit { it[KEY_INTELLIGENT_SHUFFLE] = enabled }
    }

    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS_PLAYBACK] ?: true }
    fun getGaplessPlayback(): Boolean = securePrefs.getBoolean("saved_gapless", true)
    suspend fun setGaplessPlayback(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_gapless", enabled).apply()
        context.dataStore.edit { it[KEY_GAPLESS_PLAYBACK] = enabled }
    }

    val crossfadeSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_CROSSFADE_SECONDS] ?: 0 }
    fun getCrossfadeSeconds(): Int = securePrefs.getInt("saved_crossfade", 0)
    suspend fun setCrossfadeSeconds(seconds: Int) {
        securePrefs.edit().putInt("saved_crossfade", seconds).apply()
        context.dataStore.edit { it[KEY_CROSSFADE_SECONDS] = seconds }
    }

    val continuePlaying: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONTINUE_PLAYING] ?: true }
    fun getContinuePlaying(): Boolean = securePrefs.getBoolean("saved_continue_playing", true)
    suspend fun setContinuePlaying(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_continue_playing", enabled).apply()
        context.dataStore.edit { it[KEY_CONTINUE_PLAYING] = enabled }
    }

    val queueBehavior: Flow<String> = context.dataStore.data.map { it[KEY_QUEUE_BEHAVIOR] ?: "Play Next" }
    fun getQueueBehavior(): String = securePrefs.getString("saved_queue_behavior", "Play Next") ?: "Play Next"
    suspend fun setQueueBehavior(behavior: String) {
        securePrefs.edit().putString("saved_queue_behavior", behavior).apply()
        context.dataStore.edit { it[KEY_QUEUE_BEHAVIOR] = behavior }
    }

    val recordListeningHistory: Flow<Boolean> = context.dataStore.data.map { it[KEY_RECORD_HISTORY] ?: true }
    fun getRecordListeningHistory(): Boolean = securePrefs.getBoolean("saved_record_history", true)
    suspend fun setRecordListeningHistory(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_record_history", enabled).apply()
        context.dataStore.edit { it[KEY_RECORD_HISTORY] = enabled }
    }

    val lastPlayedTrackId: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_TRACK_ID] }
    val lastPlayedPositionMs: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_POSITION_MS] ?: 0L }
    suspend fun saveLastPlayedState(trackId: String, positionMs: Long) {
        context.dataStore.edit {
            it[KEY_LAST_TRACK_ID] = trackId
            it[KEY_LAST_POSITION_MS] = positionMs
        }
    }

    // ──────────────────────────────────────────────────────────────
    // AUDIO & DSP PREFERENCES
    // ──────────────────────────────────────────────────────────────

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

    fun getSoundEngineId(): String {
        return securePrefs.getString("saved_engine_id", "dolby") ?: "dolby"
    }

    suspend fun saveSoundEngineId(engineId: String) {
        securePrefs.edit().putString("saved_engine_id", engineId).apply()
    }

    fun getEngineModeId(): String {
        return securePrefs.getString("saved_engine_mode_id", "dolby_music") ?: "dolby_music"
    }

    suspend fun saveEngineModeId(modeId: String) {
        securePrefs.edit().putString("saved_engine_mode_id", modeId).apply()
    }

    fun getSoundEngineEnabled(): Boolean {
        return securePrefs.getBoolean("saved_engine_enabled", true)
    }

    suspend fun saveSoundEngineEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_engine_enabled", enabled).apply()
    }

    fun getSoundEngineMode(): String {
        return securePrefs.getString("saved_sound_engine_mode", "DOLBY_ATMOS") ?: "DOLBY_ATMOS"
    }

    suspend fun saveSoundEngineMode(mode: String) {
        securePrefs.edit().putString("saved_sound_engine_mode", mode).apply()
        context.dataStore.edit { prefs ->
            prefs[KEY_DOLBY_ATMOS_MODE] = mode
        }
    }

    fun getDolbyAtmosMode(): String = getSoundEngineMode()
    suspend fun saveDolbyAtmosMode(mode: String) = saveSoundEngineMode(mode)

    val audioNormalization: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_NORMALIZATION] ?: true }
    fun getAudioNormalization(): Boolean = securePrefs.getBoolean("saved_audio_norm", true)
    suspend fun setAudioNormalization(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_audio_norm", enabled).apply()
        context.dataStore.edit { it[KEY_AUDIO_NORMALIZATION] = enabled }
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

    val downloadQuality: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_QUALITY] ?: "high"
    }
    fun getDownloadQuality(): String = securePrefs.getString("saved_dl_quality", "high") ?: "high"
    suspend fun setDownloadQuality(quality: String) {
        securePrefs.edit().putString("saved_dl_quality", quality).apply()
        context.dataStore.edit { it[KEY_DOWNLOAD_QUALITY] = quality }
    }

    val hapticIntensity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_INTENSITY] ?: "OFF"
    }

    val isShuffle: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHUFFLE] ?: false
    }

    val repeatMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPEAT_MODE] ?: "OFF"
    }

    fun getApiKey(): String? = securePrefs.getString(SECURE_KEY_API_KEY, null)
    fun getBaseUrl(): String = securePrefs.getString("saved_base_url", null) ?: DEFAULT_BASE_URL
    fun getAudioQuality(): String = securePrefs.getString("saved_audio_quality", null) ?: "high"

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
        context.dataStore.edit { prefs -> prefs[KEY_PROVIDER_ID] = providerId }
    }

    suspend fun setBaseUrl(baseUrl: String) {
        securePrefs.edit().putString("saved_base_url", baseUrl).apply()
        context.dataStore.edit { prefs -> prefs[KEY_BASE_URL] = baseUrl }
    }

    suspend fun setAudioQuality(quality: String) {
        securePrefs.edit().putString("saved_audio_quality", quality).apply()
        context.dataStore.edit { prefs -> prefs[KEY_AUDIO_QUALITY] = quality }
    }

    suspend fun setHapticIntensity(intensity: String) {
        context.dataStore.edit { prefs -> prefs[KEY_HAPTIC_INTENSITY] = intensity }
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_REPEAT_MODE] = mode }
    }

    // ──────────────────────────────────────────────────────────────
    // MUSIC & DISCOVERY
    // ──────────────────────────────────────────────────────────────

    val personalizedRecommendations: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PERSONALIZED_RECOMMENDATIONS] ?: true
    }
    fun getPersonalizedRecommendations(): Boolean = securePrefs.getBoolean("saved_personalized_recs", true)
    suspend fun setPersonalizedRecommendations(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_personalized_recs", enabled).apply()
        context.dataStore.edit { it[KEY_PERSONALIZED_RECOMMENDATIONS] = enabled }
    }

    val trendingRegion: Flow<String> = context.dataStore.data.map { it[KEY_TRENDING_REGION] ?: "India" }
    fun getTrendingRegion(): String = securePrefs.getString("saved_trending_region", "India") ?: "India"
    suspend fun setTrendingRegion(region: String) {
        securePrefs.edit().putString("saved_trending_region", region).apply()
        context.dataStore.edit { it[KEY_TRENDING_REGION] = region }
    }

    val newReleaseLanguage: Flow<String> = context.dataStore.data.map { it[KEY_NEW_RELEASE_LANGUAGE] ?: "Preferred Languages" }
    fun getNewReleaseLanguage(): String = securePrefs.getString("saved_new_release_lang", "Preferred Languages") ?: "Preferred Languages"
    suspend fun setNewReleaseLanguage(lang: String) {
        securePrefs.edit().putString("saved_new_release_lang", lang).apply()
        context.dataStore.edit { it[KEY_NEW_RELEASE_LANGUAGE] = lang }
    }

    val personalizationLevel: Flow<String> = context.dataStore.data.map { it[KEY_PERSONALIZATION_LEVEL] ?: "Balanced" }
    fun getPersonalizationLevel(): String = securePrefs.getString("saved_personalization_level", "Balanced") ?: "Balanced"
    suspend fun setPersonalizationLevel(level: String) {
        securePrefs.edit().putString("saved_personalization_level", level).apply()
        context.dataStore.edit { it[KEY_PERSONALIZATION_LEVEL] = level }
    }

    val trendingEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TRENDING_ENABLED] ?: true }
    suspend fun setTrendingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TRENDING_ENABLED] = enabled }
    }

    val newReleasesEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NEW_RELEASES_ENABLED] ?: true }
    suspend fun setNewReleasesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NEW_RELEASES_ENABLED] = enabled }
    }

    val discoveryEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DISCOVERY_ENABLED] ?: true }
    suspend fun setDiscoveryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DISCOVERY_ENABLED] = enabled }
    }

    val preferredArtists: Flow<Set<String>> = context.dataStore.data.map { it[KEY_PREFERRED_ARTISTS] ?: emptySet() }
    suspend fun setPreferredArtists(artists: Set<String>) {
        context.dataStore.edit { it[KEY_PREFERRED_ARTISTS] = artists }
    }

    val favoriteGenres: Flow<Set<String>> = context.dataStore.data.map { it[KEY_FAVORITE_GENRES] ?: emptySet() }
    suspend fun setFavoriteGenres(genres: Set<String>) {
        context.dataStore.edit { it[KEY_FAVORITE_GENRES] = genres }
    }

    // ──────────────────────────────────────────────────────────────
    // NOTIFICATIONS
    // ──────────────────────────────────────────────────────────────

    val newReleaseNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_NEW_RELEASES] ?: true }
    suspend fun setNewReleaseNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_NEW_RELEASES] = enabled }
    }

    val trendingNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_TRENDING] ?: true }
    suspend fun setTrendingNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_TRENDING] = enabled }
    }

    val recommendationNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_RECOMMENDATIONS] ?: true }
    suspend fun setRecommendationNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_RECOMMENDATIONS] = enabled }
    }

    val playlistActivityNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_PLAYLIST] ?: true }
    suspend fun setPlaylistActivityNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_PLAYLIST] = enabled }
    }

    val systemUpdateNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIF_SYSTEM] ?: true }
    suspend fun setSystemUpdateNotifications(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIF_SYSTEM] = enabled }
    }

    // ──────────────────────────────────────────────────────────────
    // APPEARANCE
    // ──────────────────────────────────────────────────────────────

    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "Dark" }
    fun getThemeMode(): String = securePrefs.getString("saved_theme_mode", "Dark") ?: "Dark"
    suspend fun setThemeMode(mode: String) {
        securePrefs.edit().putString("saved_theme_mode", mode).apply()
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[KEY_REDUCE_MOTION] ?: false }
    fun getReduceMotion(): Boolean = securePrefs.getBoolean("saved_reduce_motion", false)
    suspend fun setReduceMotion(enabled: Boolean) {
        securePrefs.edit().putBoolean("saved_reduce_motion", enabled).apply()
        context.dataStore.edit { it[KEY_REDUCE_MOTION] = enabled }
    }

    val interfaceEffects: Flow<String> = context.dataStore.data.map { it[KEY_INTERFACE_EFFECTS] ?: "Subtle Frosted Glass" }
    suspend fun setInterfaceEffects(effects: String) {
        context.dataStore.edit { it[KEY_INTERFACE_EFFECTS] = effects }
    }

    // ──────────────────────────────────────────────────────────────
    // STORAGE & NETWORK
    // ──────────────────────────────────────────────────────────────

    val networkUsage: Flow<String> = context.dataStore.data.map { it[KEY_NETWORK_USAGE] ?: "Allow Mobile Data" }
    fun getNetworkUsage(): String = securePrefs.getString("saved_network_usage", "Allow Mobile Data") ?: "Allow Mobile Data"
    suspend fun setNetworkUsage(usage: String) {
        securePrefs.edit().putString("saved_network_usage", usage).apply()
        context.dataStore.edit { it[KEY_NETWORK_USAGE] = usage }
    }
}

