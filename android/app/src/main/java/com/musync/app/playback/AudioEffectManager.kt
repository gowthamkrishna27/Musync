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

enum class SoundEngine(
    val id: String,
    val title: String,
    val recommendation: String,
    val iconType: String,
    val bestModeId: String
) {
    DOLBY_ATMOS("dolby", "Dolby Atmos", "3D Spatial Surround & Staging", "spatial", "dolby"),
    SONY_360("sony", "Sony 360 Reality", "360° Spherical Object Audio", "spatial", "sony"),
    DTS_X("dts", "DTS:X Ultra", "High-Energy Cinema Dynamics", "cinema", "dts"),
    BOSE_EQ("bose", "Bose ActiveEQ", "Warm Balanced Low-End Contour", "warm", "bose"),
    SENNHEISER_AMBEO("ambeo", "Sennheiser AMBEO", "Acoustic Holographic Room Depth", "spatial", "ambeo"),
    VIPER_FX("viper", "Viper Master FX", "Vacuum Tube Warmth & Sub Punch", "bolt", "viper"),
    HI_RES("hires", "Hi-Res Direct", "Bit-Perfect Lossless Master (Bypass)", "direct", "hires")
}

data class EngineMode(
    val id: String,
    val engine: SoundEngine,
    val title: String,
    val subtitle: String,
    val recommendationTag: String? = null,
    val iconType: String = "music",
    val bassStrength: Short,
    val virtStrength: Short,
    val loudnessGain: Int,
    val reverbPreset: Short,
    val bandGains: List<Short>
)

object SoundEngineRegistry {
    // 7 Actual Engines configured to their best signature mode
    val signatureEngines: List<EngineMode> = listOf(
        EngineMode(
            id = "dolby",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Atmos 3D",
            subtitle = "3D spatial soundstage & crystal vocals",
            recommendationTag = "★ Spatial 3D",
            iconType = "spatial",
            bassStrength = 450,
            virtStrength = 650,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(350, 150, 0, 250, 350)
        ),
        EngineMode(
            id = "sony",
            engine = SoundEngine.SONY_360,
            title = "Sony 360 Reality",
            subtitle = "360° spherical coordinate object audio",
            recommendationTag = "★ 360° Audio",
            iconType = "spatial",
            bassStrength = 350,
            virtStrength = 850,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(200, 150, 250, 400, 500)
        ),
        EngineMode(
            id = "dts",
            engine = SoundEngine.DTS_X,
            title = "DTS:X Ultra",
            subtitle = "Multi-channel cinema dynamics & punch",
            recommendationTag = "★ Dynamic",
            iconType = "cinema",
            bassStrength = 650,
            virtStrength = 800,
            loudnessGain = 120,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(500, 250, 0, 300, 450)
        ),
        EngineMode(
            id = "bose",
            engine = SoundEngine.BOSE_EQ,
            title = "Bose ActiveEQ",
            subtitle = "Velvety smooth low-end & acoustic contour",
            recommendationTag = "★ Warm EQ",
            iconType = "warm",
            bassStrength = 550,
            virtStrength = 350,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(450, 250, 150, 100, 150)
        ),
        EngineMode(
            id = "ambeo",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "Sennheiser AMBEO",
            subtitle = "Holographic depth & 3D room resonance",
            recommendationTag = "★ AMBEO 3D",
            iconType = "spatial",
            bassStrength = 400,
            virtStrength = 750,
            loudnessGain = 90,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(250, 150, 250, 400, 450)
        ),
        EngineMode(
            id = "viper",
            engine = SoundEngine.VIPER_FX,
            title = "Viper Master FX",
            subtitle = "Analog vacuum tube harmonics & sub punch",
            recommendationTag = "★ Tube FX",
            iconType = "bolt",
            bassStrength = 550,
            virtStrength = 450,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(450, 250, 180, 280, 380)
        ),
        EngineMode(
            id = "hires",
            engine = SoundEngine.HI_RES,
            title = "Hi-Res Direct",
            subtitle = "100% bit-perfect unaltered studio master",
            recommendationTag = "★ Lossless",
            iconType = "direct",
            bassStrength = 0,
            virtStrength = 0,
            loudnessGain = 0,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(0, 0, 0, 0, 0)
        )
    )

    val allModes: List<EngineMode> get() = signatureEngines

    fun getModesForEngine(engine: SoundEngine): List<EngineMode> {
        return signatureEngines.filter { it.engine == engine }
    }

    fun findModeById(modeId: String): EngineMode {
        return signatureEngines.find { it.id == modeId || it.engine.id == modeId } ?: signatureEngines.first()
    }
}

// Compatibility Alias
typealias SoundEngineMode = SoundEngine
typealias DolbyAtmosMode = SoundEngine

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentEngine: SoundEngine = SoundEngine.DOLBY_ATMOS,
    val currentMode: EngineMode = SoundEngineRegistry.signatureEngines.first(),
    val activePreset: String = "Dolby Atmos 3D",
    val bassBoostStrength: Short = 450,
    val virtualizerStrength: Short = 650,
    val loudnessGainMb: Int = 80,
    val reverbPreset: Short = PresetReverb.PRESET_SMALLROOM,
    val bands: List<EqualizerBand> = listOf(
        EqualizerBand(0, 60, 350, -1500, 1500),
        EqualizerBand(1, 230, 150, -1500, 1500),
        EqualizerBand(2, 910, 0, -1500, 1500),
        EqualizerBand(3, 3600, 250, -1500, 1500),
        EqualizerBand(4, 14000, 350, -1500, 1500)
    )
) {
    val soundEngine: SoundEngine
        get() = currentEngine

    val dolbyMode: SoundEngine
        get() = currentEngine

    val isDolbyActive: Boolean
        get() = isEnabled && currentEngine != SoundEngine.HI_RES
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
        scope.launch {
            val savedEngineId = preferencesManager.getSoundEngineId()
            val savedEnabled = preferencesManager.getSoundEngineEnabled()
            val mode = SoundEngineRegistry.signatureEngines.find { it.engine.id == savedEngineId || it.id == savedEngineId }
                ?: SoundEngineRegistry.signatureEngines.first()
            _state.update { it.copy(isEnabled = savedEnabled) }
            applyEngineModeInternal(mode)
        }
    }

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (currentSessionId == audioSessionId && equalizer != null) return

        detach()
        currentSessionId = audioSessionId
        Log.i(TAG, "Attaching Sound Engine hardware audiofx to session: $audioSessionId")

        try {
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.e(TAG, "Equalizer init failed: ${e.message}")
            }

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

            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(_state.value.loudnessGainMb)
                    enabled = _state.value.isEnabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer not supported: ${e.message}")
            }

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

            applyEngineModeInternal(_state.value.currentMode)

            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            }
            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Sound Engines attached successfully to session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing sound engine effects: ${e.message}", e)
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
            if (enabled) {
                applyEngineModeInternal(_state.value.currentMode)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling audiofx: ${e.message}")
        }
        scope.launch {
            preferencesManager.saveSoundEngineEnabled(enabled)
        }
    }

    fun selectEngine(engine: SoundEngine) {
        val mode = SoundEngineRegistry.signatureEngines.find { it.engine == engine }
            ?: SoundEngineRegistry.signatureEngines.first()
        setEngineMode(mode)
    }

    fun setEngineMode(mode: EngineMode) {
        applyEngineModeInternal(mode)
        scope.launch {
            preferencesManager.saveSoundEngineId(mode.engine.id)
            preferencesManager.saveEngineModeId(mode.id)
        }
    }

    fun setSoundEngine(engine: SoundEngine) {
        selectEngine(engine)
    }

    fun setDolbyMode(engine: SoundEngine) {
        selectEngine(engine)
    }

    private fun applyEngineModeInternal(mode: EngineMode) {
        val numBands = equalizer?.numberOfBands?.toInt() ?: _state.value.bands.size.takeIf { it > 0 } ?: 5
        val minLevel = equalizer?.bandLevelRange?.getOrNull(0) ?: -1500
        val maxLevel = equalizer?.bandLevelRange?.getOrNull(1) ?: 1500

        try {
            equalizer?.let { eq ->
                for (i in 0 until minOf(numBands, mode.bandGains.size)) {
                    val gain = mode.bandGains[i].coerceIn(minLevel, maxLevel)
                    eq.setBandLevel(i.toShort(), gain)
                }
            }

            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(mode.bassStrength)
                }
            }

            virtualizer?.let { v ->
                if (v.strengthSupported) {
                    v.setStrength(mode.virtStrength)
                }
            }

            loudnessEnhancer?.setTargetGain(mode.loudnessGain)

            presetReverb?.let { pr ->
                pr.preset = mode.reverbPreset
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying engine mode ${mode.id}: ${e.message}")
        }

        _state.update { curr ->
            val updatedBands = curr.bands.mapIndexed { idx, band ->
                val gain = mode.bandGains.getOrNull(idx) ?: 0
                band.copy(levelMb = gain)
            }
            curr.copy(
                currentEngine = mode.engine,
                currentMode = mode,
                activePreset = mode.title,
                bassBoostStrength = mode.bassStrength,
                virtualizerStrength = mode.virtStrength,
                loudnessGainMb = mode.loudnessGain,
                reverbPreset = mode.reverbPreset,
                bands = if (updatedBands.isNotEmpty()) updatedBands else curr.bands
            )
        }
    }
}
