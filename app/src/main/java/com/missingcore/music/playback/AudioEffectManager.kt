package com.missingcore.music.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.missingcore.music.data.datastore.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Short, // millibels (-1500 to +1500)
    val minLevelMb: Short,
    val maxLevelMb: Short
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val activePreset: String = "Bass Boost",
    val bassBoostStrength: Short = 750, // 0 to 1000
    val virtualizerStrength: Short = 300, // 0 to 1000
    val loudnessGainMb: Int = 200, // 0 to 1000 mB
    val bands: List<EqualizerBand> = listOf(
        EqualizerBand(0, 60, 700, -1500, 1500),
        EqualizerBand(1, 230, 400, -1500, 1500),
        EqualizerBand(2, 910, 0, -1500, 1500),
        EqualizerBand(3, 3600, 100, -1500, 1500),
        EqualizerBand(4, 14000, 200, -1500, 1500)
    ),
    val availablePresets: List<String> = listOf("Flat", "Bass Boost", "Vocal Focus", "Treble Boost", "Rock", "Electronic", "Custom")
)

class AudioEffectManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "AudioEffectManager"
        private const val PRIORITY = 0
    }

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load initial state
        scope.launch {
            preferencesManager.getEqualizerPreset().let { preset ->
                applyPresetInternal(preset)
            }
        }
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (currentSessionId == audioSessionId && equalizer != null) return

        detach()
        currentSessionId = audioSessionId
        Log.d(TAG, "Attaching Audio Effects to audioSessionId: $audioSessionId")

        try {
            // 1. Equalizer
            equalizer = Equalizer(PRIORITY, audioSessionId).apply {
                enabled = _state.value.isEnabled
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
            bassBoost = BassBoost(PRIORITY, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength)
                }
                enabled = _state.value.isEnabled
            }

            // 3. Virtualizer (Spatial Surround)
            virtualizer = Virtualizer(PRIORITY, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.virtualizerStrength)
                }
                enabled = _state.value.isEnabled
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

            _state.update { it.copy(bands = bandsList) }

            // Re-apply active preset parameters to hardware
            applyPresetInternal(_state.value.activePreset)

            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)

            Log.d(TAG, "✓ Hardware Audio Effects attached successfully with $numBands bands")
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
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling audiofx: ${e.message}")
        }
    }

    fun setPreset(presetName: String) {
        applyPresetInternal(presetName)
        scope.launch {
            preferencesManager.saveEqualizerPreset(presetName)
        }
    }

    private fun applyPresetInternal(presetName: String) {
        val numBands = equalizer?.numberOfBands?.toInt() ?: _state.value.bands.size.takeIf { it > 0 } ?: 5
        val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
        val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500

        var bassStrength: Short = 0
        var virtStrength: Short = 0
        var loudnessGain = 0

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
            curr.copy(bands = updated, activePreset = "Custom")
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
        _state.update { it.copy(bassBoostStrength = clamped, activePreset = "Custom") }
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
        _state.update { it.copy(virtualizerStrength = clamped, activePreset = "Custom") }
    }

    fun setLoudness(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1000)
        try {
            loudnessEnhancer?.setTargetGain(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting loudness: ${e.message}")
        }
        _state.update { it.copy(loudnessGainMb = clamped, activePreset = "Custom") }
    }
}
