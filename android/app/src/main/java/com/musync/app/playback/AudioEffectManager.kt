package com.musync.app.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.musync.app.data.local.datastore.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DolbyAtmosMode(val label: String, val shortDesc: String) {
    OFF("Off", "Stereo bypass"),
    MUSIC("Music", "3D soundstage & crystal vocal presence"),
    CINEMA("Cinema", "360° surround & deep sub-bass rumble"),
    STUDIO("Studio", "Intimate room acoustics & dialogue clarity")
}

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Short, // millibels (-1500 to +1500)
    val minLevelMb: Short,
    val maxLevelMb: Short
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val dolbyMode: DolbyAtmosMode = DolbyAtmosMode.OFF,
    val activePreset: String = "Bass Boost",
    val bassBoostStrength: Short = 750, // 0 to 1000
    val virtualizerStrength: Short = 300, // 0 to 1000
    val loudnessGainMb: Int = 200, // 0 to 1000 mB
    val reverbPreset: Short = PresetReverb.PRESET_NONE,
    val bands: List<EqualizerBand> = listOf(
        EqualizerBand(0, 60, 700, -1500, 1500),
        EqualizerBand(1, 230, 400, -1500, 1500),
        EqualizerBand(2, 910, 0, -1500, 1500),
        EqualizerBand(3, 3600, 100, -1500, 1500),
        EqualizerBand(4, 14000, 200, -1500, 1500)
    ),
    val availablePresets: List<String> = listOf("Flat", "Bass Boost", "Vocal Focus", "Treble Boost", "Rock", "Electronic", "Dolby Atmos", "Custom")
) {
    val isDolbyActive: Boolean
        get() = isEnabled && dolbyMode != DolbyAtmosMode.OFF
}

class AudioEffectManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "AudioEffectManager"
    }

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load initial state and saved presets
        scope.launch {
            val savedPreset = preferencesManager.getEqualizerPreset()
            val savedDolby = preferencesManager.getDolbyAtmosMode()
            val dolbyEnum = try {
                DolbyAtmosMode.valueOf(savedDolby)
            } catch (_: Exception) {
                DolbyAtmosMode.OFF
            }

            if (dolbyEnum != DolbyAtmosMode.OFF) {
                applyDolbyInternal(dolbyEnum)
            } else {
                applyPresetInternal(savedPreset)
            }
        }
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (currentSessionId == audioSessionId && equalizer != null) return

        detach()
        currentSessionId = audioSessionId
        Log.i(TAG, "Attaching Audio Effects & Dolby Atmos Engine to audioSessionId: $audioSessionId")

        try {
            // 1. Hardware Equalizer
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer init failed for session $audioSessionId: ${e.message}")
            }

            // Extract hardware bands
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0
            val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
            val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500

            val bandsList = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val freq = (equalizer?.getCenterFreq(i.toShort()) ?: 0) / 1000
                val currentLevel = equalizer?.getBandLevel(i.toShort()) ?: 0
                bandsList.add(
                    EqualizerBand(
                        index = i,
                        centerFreqHz = freq,
                        levelMb = currentLevel,
                        minLevelMb = minLevel,
                        maxLevelMb = maxLevel
                    )
                )
            }

            // 2. Bass Boost
            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    if (strengthSupported) {
                        setStrength(_state.value.bassBoostStrength)
                    }
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost not supported: ${e.message}")
            }

            // 3. Virtualizer (Spatial 3D Sound)
            try {
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    if (strengthSupported) {
                        setStrength(_state.value.virtualizerStrength)
                    }
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not supported: ${e.message}")
            }

            // 4. Loudness Enhancer
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(_state.value.loudnessGainMb)
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer not supported: ${e.message}")
            }

            // 5. Preset Reverb (Acoustic Soundstage)
            try {
                presetReverb = PresetReverb(0, audioSessionId).apply {
                    preset = _state.value.reverbPreset
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "PresetReverb not supported: ${e.message}")
            }

            if (bandsList.isNotEmpty()) {
                _state.update { it.copy(bands = bandsList) }
            }

            // Re-apply active state to newly attached hardware session
            if (_state.value.dolbyMode != DolbyAtmosMode.OFF) {
                applyDolbyInternal(_state.value.dolbyMode)
            } else {
                applyPresetInternal(_state.value.activePreset)
            }

            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Dolby Atmos & Hardware Audio Effects attached successfully to session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing hardware audiofx: ${e.message}", e)
        }
    }

    @Synchronized
    fun detach() {
        try {
            if (currentSessionId != 0) {
                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, currentSessionId)
                    putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                }
                context.sendBroadcast(intent)
            }
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            presetReverb?.release()
            presetReverb = null
            currentSessionId = 0
            Log.d(TAG, "Detached Audio Effects")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audiofx: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(isEnabled = enabled) }
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
            presetReverb?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling audiofx: ${e.message}")
        }
    }

    /**
     * Activates a Dolby Atmos 3D Spatial Audio profile.
     */
    fun setDolbyMode(mode: DolbyAtmosMode) {
        applyDolbyInternal(mode)
        scope.launch {
            preferencesManager.saveDolbyAtmosMode(mode.name)
        }
    }

    private fun applyDolbyInternal(mode: DolbyAtmosMode) {
        if (mode == DolbyAtmosMode.OFF) {
            _state.update { it.copy(dolbyMode = DolbyAtmosMode.OFF) }
            applyPresetInternal(_state.value.activePreset)
            return
        }

        val numBands = equalizer?.numberOfBands?.toInt() ?: _state.value.bands.size.takeIf { it > 0 } ?: 5
        val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
        val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500

        val (bassStrength: Short, virtStrength: Short, loudnessGain: Int, reverb: Short, bandGains: List<Short>) = when (mode) {
            DolbyAtmosMode.MUSIC -> {
                // Wide 3D soundstage, clear vocal separation, tight punchy bass
                val gains = listOf(500.toShort(), 200.toShort(), 0.toShort(), 350.toShort(), 450.toShort())
                Tuple5(550.toShort(), 700.toShort(), 200, PresetReverb.PRESET_SMALLROOM, gains)
            }
            DolbyAtmosMode.CINEMA -> {
                // Maximum 360° spatial immersion, deep sub-woofer resonance, concert decay
                val gains = listOf(750.toShort(), 300.toShort(), (-50).toShort(), 400.toShort(), 600.toShort())
                Tuple5(850.toShort(), 950.toShort(), 350, PresetReverb.PRESET_MEDIUMHALL, gains)
            }
            DolbyAtmosMode.STUDIO -> {
                // Intimate acoustic room, elevated dialogue & vocal presence, zero echo
                val gains = listOf(200.toShort(), 100.toShort(), 350.toShort(), 500.toShort(), 300.toShort())
                Tuple5(300.toShort(), 450.toShort(), 150, PresetReverb.PRESET_SMALLROOM, gains)
            }
            DolbyAtmosMode.OFF -> {
                val gains = listOf(0.toShort(), 0.toShort(), 0.toShort(), 0.toShort(), 0.toShort())
                Tuple5(0.toShort(), 0.toShort(), 0, PresetReverb.PRESET_NONE, gains)
            }
        }

        try {
            equalizer?.let { eq ->
                for (i in 0 until minOf(numBands, bandGains.size)) {
                    val gain = bandGains[i].coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(i.toShort(), gain)
                }
            }

            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(bassStrength)
                }
            }

            virtualizer?.let { v ->
                if (v.strengthSupported) {
                    v.setStrength(virtStrength)
                }
            }

            loudnessEnhancer?.setTargetGain(loudnessGain)

            presetReverb?.let { pr ->
                pr.preset = reverb
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying Dolby mode $mode: ${e.message}")
        }

        _state.update { curr ->
            val updatedBands = curr.bands.mapIndexed { idx, band ->
                val gain = bandGains.getOrNull(idx) ?: 0
                band.copy(levelMb = gain.toShort())
            }
            curr.copy(
                dolbyMode = mode,
                activePreset = "Dolby Atmos",
                bassBoostStrength = bassStrength,
                virtualizerStrength = virtStrength,
                loudnessGainMb = loudnessGain,
                reverbPreset = reverb,
                bands = if (updatedBands.isNotEmpty()) updatedBands else curr.bands
            )
        }
    }

    fun setPreset(presetName: String) {
        if (presetName == "Dolby Atmos") {
            setDolbyMode(DolbyAtmosMode.MUSIC)
            return
        }
        _state.update { it.copy(dolbyMode = DolbyAtmosMode.OFF) }
        applyPresetInternal(presetName)
        scope.launch {
            preferencesManager.saveEqualizerPreset(presetName)
            preferencesManager.saveDolbyAtmosMode(DolbyAtmosMode.OFF.name)
        }
    }

    private fun applyPresetInternal(presetName: String) {
        val numBands = equalizer?.numberOfBands?.toInt() ?: _state.value.bands.size.takeIf { it > 0 } ?: 5
        val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
        val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500

        var bassStrength: Short = 0
        var virtStrength: Short = 0
        var loudnessGain = 0
        var reverb: Short = PresetReverb.PRESET_NONE

        // Calculated band levels in millibels (-1500mB to +1500mB)
        val bandGains: List<Short> = when (presetName) {
            "Bass Boost" -> {
                bassStrength = 800
                virtStrength = 200
                loudnessGain = 250
                listOf(700.toShort(), 400.toShort(), 0.toShort(), 100.toShort(), 200.toShort())
            }
            "Vocal Focus" -> {
                bassStrength = 100
                virtStrength = 300
                loudnessGain = 200
                listOf((-200).toShort(), 200.toShort(), 600.toShort(), 400.toShort(), (-100).toShort())
            }
            "Treble Boost" -> {
                bassStrength = 100
                virtStrength = 150
                loudnessGain = 150
                listOf(0.toShort(), 0.toShort(), 200.toShort(), 600.toShort(), 900.toShort())
            }
            "Rock" -> {
                bassStrength = 650
                virtStrength = 400
                loudnessGain = 300
                listOf(600.toShort(), 300.toShort(), (-100).toShort(), 400.toShort(), 700.toShort())
            }
            "Electronic" -> {
                bassStrength = 750
                virtStrength = 500
                loudnessGain = 350
                listOf(700.toShort(), 300.toShort(), 0.toShort(), 500.toShort(), 800.toShort())
            }
            "Dolby Atmos" -> {
                bassStrength = 600
                virtStrength = 750
                loudnessGain = 250
                reverb = PresetReverb.PRESET_SMALLROOM
                listOf(500.toShort(), 200.toShort(), 0.toShort(), 350.toShort(), 450.toShort())
            }
            else -> { // "Flat" or default
                bassStrength = 0
                virtStrength = 0
                loudnessGain = 0
                listOf(0.toShort(), 0.toShort(), 0.toShort(), 0.toShort(), 0.toShort())
            }
        }

        try {
            equalizer?.let { eq ->
                for (i in 0 until minOf(numBands, bandGains.size)) {
                    val gain = bandGains[i].coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(i.toShort(), gain)
                }
            }

            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(bassStrength)
                }
            }

            virtualizer?.let { v ->
                if (v.strengthSupported) {
                    v.setStrength(virtStrength)
                }
            }

            loudnessEnhancer?.setTargetGain(loudnessGain)

            presetReverb?.let { pr ->
                pr.preset = reverb
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying preset $presetName: ${e.message}")
        }

        _state.update { curr ->
            val updatedBands = curr.bands.mapIndexed { idx, band ->
                val gain = bandGains.getOrNull(idx) ?: 0
                band.copy(levelMb = gain.toShort())
            }
            curr.copy(
                activePreset = presetName,
                bassBoostStrength = bassStrength,
                virtualizerStrength = virtStrength,
                loudnessGainMb = loudnessGain,
                reverbPreset = reverb,
                bands = if (updatedBands.isNotEmpty()) updatedBands else curr.bands
            )
        }
    }

    fun setBandLevel(bandIndex: Int, levelMb: Short) {
        try {
            val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
            val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500
            val clamped = levelMb.coerceIn(minLevel, maxLevel)
            equalizer?.setBandLevel(bandIndex.toShort(), clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting band $bandIndex: ${e.message}")
        }

        _state.update { curr ->
            val updated = curr.bands.map { band ->
                if (band.index == bandIndex) band.copy(levelMb = levelMb) else band
            }
            curr.copy(bands = updated, activePreset = "Custom", dolbyMode = DolbyAtmosMode.OFF)
        }
    }

    fun setBassBoost(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.let {
                if (it.strengthSupported) it.setStrength(clamped)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting bass boost: ${e.message}")
        }
        _state.update { it.copy(bassBoostStrength = clamped, activePreset = "Custom", dolbyMode = DolbyAtmosMode.OFF) }
    }

    fun setVirtualizer(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            virtualizer?.let {
                if (it.strengthSupported) it.setStrength(clamped)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting virtualizer: ${e.message}")
        }
        _state.update { it.copy(virtualizerStrength = clamped, activePreset = "Custom", dolbyMode = DolbyAtmosMode.OFF) }
    }

    fun setLoudness(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1000)
        try {
            loudnessEnhancer?.setTargetGain(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting loudness: ${e.message}")
        }
        _state.update { it.copy(loudnessGainMb = clamped, activePreset = "Custom", dolbyMode = DolbyAtmosMode.OFF) }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
