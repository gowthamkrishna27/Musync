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
    DOLBY_ATMOS("dolby", "Dolby Atmos 3D", "3D Spatial Surround & Cinema Sound", "dolby", "dolby_music"),
    SONY_360("sony", "Sony 360 Reality", "360° Spherical Object Audio", "spatial", "sony_immersion"),
    DTS_X("dts", "DTS:X Ultra", "High-Energy Surround & Punchy Bass", "dts", "dts_cinema"),
    BOSE_EQ("bose", "Bose ActiveEQ", "Warm Balanced Lows & Smooth Mids", "warm", "bose_warm"),
    SENNHEISER_AMBEO("ambeo", "Sennheiser AMBEO", "Acoustic Holographic Room Depth", "ambeo", "ambeo_boost"),
    VIPER_FX("viper", "Viper Master FX", "Deep Bass Exciter & Tube Warmth", "bolt", "viper_tube"),
    HI_RES("hires", "Hi-Res Direct", "Bit-Perfect Lossless Master (Bypass)", "direct", "hires_direct")
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
    val allModes: List<EngineMode> = listOf(
        // 1. Dolby Atmos Modes (Clean, non-clipping studio spatial profile)
        EngineMode(
            id = "dolby_music",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Music",
            subtitle = "3D soundstage & crystal vocals",
            recommendationTag = "★ Daily",
            iconType = "music",
            bassStrength = 450,
            virtStrength = 650,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(350, 150, 0, 250, 350)
        ),
        EngineMode(
            id = "dolby_cinema",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Cinema",
            subtitle = "360° surround & deep sub-bass",
            recommendationTag = "★ Cinema",
            iconType = "cinema",
            bassStrength = 650,
            virtStrength = 850,
            loudnessGain = 120,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(550, 250, -50, 350, 450)
        ),
        EngineMode(
            id = "dolby_studio",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Studio",
            subtitle = "Acoustic room & dialogue focus",
            recommendationTag = "★ Vocals",
            iconType = "studio",
            bassStrength = 200,
            virtStrength = 400,
            loudnessGain = 60,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(150, 80, 300, 350, 200)
        ),
        EngineMode(
            id = "dolby_bass",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dynamic Bass Matrix",
            subtitle = "Sub-harmonic punch & zero echo",
            recommendationTag = "★ Bass",
            iconType = "bolt",
            bassStrength = 750,
            virtStrength = 300,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(650, 300, 0, 150, 200)
        ),

        // 2. Sony 360 Reality Audio Modes
        EngineMode(
            id = "sony_immersion",
            engine = SoundEngine.SONY_360,
            title = "360° Immersion",
            subtitle = "Spherical 3D coordinate audio",
            recommendationTag = "★ 360°",
            iconType = "spatial",
            bassStrength = 350,
            virtStrength = 850,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(200, 150, 250, 400, 500)
        ),
        EngineMode(
            id = "sony_arena",
            engine = SoundEngine.SONY_360,
            title = "Live Arena",
            subtitle = "Stadium echo & concert stage",
            recommendationTag = "★ Concert",
            iconType = "music",
            bassStrength = 450,
            virtStrength = 750,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(350, 200, 200, 300, 400)
        ),
        EngineMode(
            id = "sony_vocal",
            engine = SoundEngine.SONY_360,
            title = "Vocal Stage",
            subtitle = "Center vocal isolation & air",
            recommendationTag = "★ Acoustic",
            iconType = "vocal",
            bassStrength = 150,
            virtStrength = 550,
            loudnessGain = 60,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(0, 100, 450, 350, 300)
        ),

        // 3. DTS:X Ultra Modes
        EngineMode(
            id = "dts_cinema",
            engine = SoundEngine.DTS_X,
            title = "DTS:X Surround",
            subtitle = "Multi-channel cinema dynamics",
            recommendationTag = "★ Surround",
            iconType = "cinema",
            bassStrength = 650,
            virtStrength = 800,
            loudnessGain = 120,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(500, 250, 0, 300, 450)
        ),
        EngineMode(
            id = "dts_impact",
            engine = SoundEngine.DTS_X,
            title = "Heavy Impact",
            subtitle = "Max limiter punch for EDM & Rock",
            recommendationTag = "★ EDM",
            iconType = "bolt",
            bassStrength = 750,
            virtStrength = 650,
            loudnessGain = 140,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(600, 300, -50, 350, 500)
        ),
        EngineMode(
            id = "dts_music",
            engine = SoundEngine.DTS_X,
            title = "Dynamic Studio",
            subtitle = "Balanced fidelity & crisp treble",
            recommendationTag = "★ Studio",
            iconType = "studio",
            bassStrength = 450,
            virtStrength = 600,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(400, 200, 100, 250, 350)
        ),

        // 4. Bose ActiveEQ Modes
        EngineMode(
            id = "bose_warm",
            engine = SoundEngine.BOSE_EQ,
            title = "Warm Balance",
            subtitle = "Velvety smooth low-end",
            recommendationTag = "★ Balanced",
            iconType = "warm",
            bassStrength = 550,
            virtStrength = 350,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(450, 250, 150, 100, 150)
        ),
        EngineMode(
            id = "bose_deep",
            engine = SoundEngine.BOSE_EQ,
            title = "Deep Bass",
            subtitle = "Dynamic active sub-bass contour",
            recommendationTag = "★ Deep",
            iconType = "bolt",
            bassStrength = 700,
            virtStrength = 200,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(600, 350, 50, 150, 200)
        ),
        EngineMode(
            id = "bose_acoustic",
            engine = SoundEngine.BOSE_EQ,
            title = "Acoustic Clarity",
            subtitle = "String separation & vocal lift",
            recommendationTag = "★ Clarity",
            iconType = "vocal",
            bassStrength = 250,
            virtStrength = 400,
            loudnessGain = 60,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(150, 100, 350, 400, 350)
        ),

        // 5. Sennheiser AMBEO 3D Modes
        EngineMode(
            id = "ambeo_boost",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO 3D Boost",
            subtitle = "Holographic depth & micro-reverb",
            recommendationTag = "★ 3D",
            iconType = "spatial",
            bassStrength = 400,
            virtStrength = 750,
            loudnessGain = 90,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(250, 150, 250, 400, 450)
        ),
        EngineMode(
            id = "ambeo_natural",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO Natural",
            subtitle = "Natural acoustic room decay",
            recommendationTag = "★ Natural",
            iconType = "studio",
            bassStrength = 250,
            virtStrength = 450,
            loudnessGain = 50,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(100, 80, 200, 300, 250)
        ),
        EngineMode(
            id = "ambeo_concert",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO Concert",
            subtitle = "Concert hall acoustic resonance",
            recommendationTag = "★ Stage",
            iconType = "cinema",
            bassStrength = 500,
            virtStrength = 800,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(400, 200, 150, 300, 400)
        ),

        // 6. Viper Dynamic Master Modes
        EngineMode(
            id = "viper_tube",
            engine = SoundEngine.VIPER_FX,
            title = "Tube Warmth",
            subtitle = "Analog vacuum tube harmonics",
            recommendationTag = "★ Vintage",
            iconType = "warm",
            bassStrength = 550,
            virtStrength = 450,
            loudnessGain = 100,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(450, 250, 180, 280, 380)
        ),
        EngineMode(
            id = "viper_exciter",
            engine = SoundEngine.VIPER_FX,
            title = "Sub Exciter",
            subtitle = "Heavy club subwoofer slam",
            recommendationTag = "★ Basshead",
            iconType = "bolt",
            bassStrength = 750,
            virtStrength = 500,
            loudnessGain = 120,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(700, 380, 80, 300, 450)
        ),
        EngineMode(
            id = "viper_air",
            engine = SoundEngine.VIPER_FX,
            title = "Clarity & Air",
            subtitle = "Upper treble air & wide imaging",
            recommendationTag = "★ Air",
            iconType = "spatial",
            bassStrength = 300,
            virtStrength = 600,
            loudnessGain = 80,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(150, 80, 200, 500, 650)
        ),

        // 7. Hi-Res Direct Modes
        EngineMode(
            id = "hires_direct",
            engine = SoundEngine.HI_RES,
            title = "Bit-Perfect Direct",
            subtitle = "100% Lossless unaltered master",
            recommendationTag = "★ Hi-Res",
            iconType = "direct",
            bassStrength = 0,
            virtStrength = 0,
            loudnessGain = 0,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(0, 0, 0, 0, 0)
        )
    )

    fun getModesForEngine(engine: SoundEngine): List<EngineMode> {
        return allModes.filter { it.engine == engine }
    }

    fun findModeById(modeId: String): EngineMode {
        return allModes.find { it.id == modeId } ?: allModes.first()
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
    val currentMode: EngineMode = SoundEngineRegistry.allModes.first(),
    val activePreset: String = "Dolby Music",
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
            val savedModeId = preferencesManager.getEngineModeId()
            val engine = SoundEngine.values().find { it.id == savedEngineId } ?: SoundEngine.DOLBY_ATMOS
            val mode = SoundEngineRegistry.allModes.find { it.id == savedModeId } ?: SoundEngineRegistry.getModesForEngine(engine).firstOrNull() ?: SoundEngineRegistry.allModes.first()
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
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling audiofx: ${e.message}")
        }
    }

    fun selectEngine(engine: SoundEngine) {
        val defaultMode = SoundEngineRegistry.allModes.find { it.id == engine.bestModeId }
            ?: SoundEngineRegistry.getModesForEngine(engine).firstOrNull()
            ?: SoundEngineRegistry.allModes.first()
        setEngineMode(defaultMode)
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
