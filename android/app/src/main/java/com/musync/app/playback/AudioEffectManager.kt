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
    val bestModeId: String
) {
    DOLBY_ATMOS("dolby", "Dolby Atmos 3D", "Recommended for 3D spatial soundstage & cinematic depth", "dolby_music"),
    SONY_360("sony", "Sony 360 Reality", "Recommended for 360° object sound & live concert recordings", "sony_immersion"),
    DTS_X("dts", "DTS:X Ultra", "Recommended for high-energy EDM, rock & dynamic punch", "dts_cinema"),
    BOSE_EQ("bose", "Bose ActiveEQ", "Recommended for warm velvety lows & non-fatiguing daily listening", "bose_warm"),
    SENNHEISER_AMBEO("ambeo", "Sennheiser AMBEO", "Recommended for acoustic tracks & holographic room decay", "ambeo_boost"),
    VIPER_FX("viper", "Viper Master FX", "Recommended for heavy bassheads & analog tube warmth", "viper_tube"),
    HI_RES("hires", "Hi-Res Direct", "Recommended for bit-perfect audiophile studio masters", "hires_direct")
}

data class EngineMode(
    val id: String,
    val engine: SoundEngine,
    val title: String,
    val description: String,
    val recommendationTag: String? = null,
    val traitChips: List<String> = emptyList(),
    val bassStrength: Short,
    val virtStrength: Short,
    val loudnessGain: Int,
    val reverbPreset: Short,
    val bandGains: List<Short>
)

object SoundEngineRegistry {
    val allModes: List<EngineMode> = listOf(
        // 1. Dolby Atmos Modes
        EngineMode(
            id = "dolby_music",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Music",
            description = "Expanded 3D soundstage, clear vocal separation & tight punchy bass",
            recommendationTag = "★ BEST FOR DAILY LISTENING",
            traitChips = listOf("+5.0dB Sub", "3D Soundstage", "Vocal Clarity"),
            bassStrength = 650,
            virtStrength = 750,
            loudnessGain = 220,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(500, 200, 0, 350, 450)
        ),
        EngineMode(
            id = "dolby_cinema",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Cinema",
            description = "360° spherical immersion, concert hall reverb & deep sub-woofer rumble",
            recommendationTag = "★ BEST FOR IMMERSIVE CINEMA",
            traitChips = listOf("+7.5dB Subwoofer", "360° Sphere", "Hall Reverb"),
            bassStrength = 850,
            virtStrength = 950,
            loudnessGain = 320,
            reverbPreset = PresetReverb.PRESET_MEDIUMHALL,
            bandGains = listOf(750, 350, -50, 450, 650)
        ),
        EngineMode(
            id = "dolby_studio",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dolby Studio",
            description = "Intimate room acoustics with elevated dialogue clarity and zero echo",
            recommendationTag = "★ BEST FOR PODCASTS & VOCALS",
            traitChips = listOf("Vocal Isolation", "Room Acoustics", "+5.0dB Mids"),
            bassStrength = 300,
            virtStrength = 500,
            loudnessGain = 150,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(200, 100, 350, 500, 300)
        ),
        EngineMode(
            id = "dolby_bass",
            engine = SoundEngine.DOLBY_ATMOS,
            title = "Dynamic Bass Matrix",
            description = "Sub-harmonic bass exciter with tight and punchy transient response",
            recommendationTag = "★ BEST FOR PUNCHY BASS",
            traitChips = listOf("+9.5dB Low-End", "Fast Transients", "Zero Echo"),
            bassStrength = 950,
            virtStrength = 400,
            loudnessGain = 300,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(850, 450, 0, 200, 300)
        ),

        // 2. Sony 360 Reality Audio Modes
        EngineMode(
            id = "sony_immersion",
            engine = SoundEngine.SONY_360,
            title = "360° Object Immersion",
            description = "Spherical 3D field with instruments placed in surrounding coordinate space",
            recommendationTag = "★ BEST FOR SPATIAL 360°",
            traitChips = listOf("3D Coordinates", "Air Separation", "+6.5dB Treble"),
            bassStrength = 450,
            virtStrength = 950,
            loudnessGain = 200,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(300, 200, 400, 550, 650)
        ),
        EngineMode(
            id = "sony_arena",
            engine = SoundEngine.SONY_360,
            title = "Live Arena Sound",
            description = "Expanded stadium acoustics with wide acoustic boundary reflections",
            recommendationTag = "★ BEST FOR CONCERTS",
            traitChips = listOf("Stadium Echo", "Wide Soundstage", "Concert Bass"),
            bassStrength = 600,
            virtStrength = 900,
            loudnessGain = 250,
            reverbPreset = PresetReverb.PRESET_LARGEHALL,
            bandGains = listOf(450, 250, 300, 400, 500)
        ),
        EngineMode(
            id = "sony_vocal",
            engine = SoundEngine.SONY_360,
            title = "Vocal Center Stage",
            description = "Front-anchored vocal isolation with surrounding airy background instruments",
            recommendationTag = "★ BEST FOR ACOUSTIC & VOCALS",
            traitChips = listOf("Center Vocal Lock", "+6.0dB Presence", "Crisp Air"),
            bassStrength = 200,
            virtStrength = 700,
            loudnessGain = 180,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(0, 150, 600, 500, 400)
        ),

        // 3. DTS:X Ultra Modes
        EngineMode(
            id = "dts_cinema",
            engine = SoundEngine.DTS_X,
            title = "DTS:X Cinema",
            description = "High dynamic range multi-channel surround with deep sub-bass roar",
            recommendationTag = "★ BEST FOR SURROUND SOUND",
            traitChips = listOf("Cinema Dynamics", "+7.0dB Sub", "Multi-Channel"),
            bassStrength = 850,
            virtStrength = 950,
            loudnessGain = 350,
            reverbPreset = PresetReverb.PRESET_MEDIUMHALL,
            bandGains = listOf(700, 350, 0, 450, 600)
        ),
        EngineMode(
            id = "dts_impact",
            engine = SoundEngine.DTS_X,
            title = "Heavy Impact",
            description = "Maximum transient slam & high-gain punch optimized for EDM and Rock",
            recommendationTag = "★ BEST FOR EDM & ROCK",
            traitChips = listOf("High Limiter Gain", "+8.5dB Kick", "Fast Attack"),
            bassStrength = 950,
            virtStrength = 850,
            loudnessGain = 400,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(850, 400, -100, 500, 700)
        ),
        EngineMode(
            id = "dts_music",
            engine = SoundEngine.DTS_X,
            title = "Dynamic Studio",
            description = "Balanced spatial multi-channel audio with crystal treble presence",
            recommendationTag = "★ BEST FOR STUDIO DYNAMICS",
            traitChips = listOf("Balanced DSP", "+6.0dB Lows", "Crisp Treble"),
            bassStrength = 650,
            virtStrength = 800,
            loudnessGain = 250,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(600, 300, 150, 400, 500)
        ),

        // 4. Bose ActiveEQ Modes
        EngineMode(
            id = "bose_warm",
            engine = SoundEngine.BOSE_EQ,
            title = "Warm Balance",
            description = "Velvety smooth lows, rich midrange presence, and fatigue-free highs",
            recommendationTag = "★ BEST FOR BALANCED AUDIO",
            traitChips = listOf("Velvety Warmth", "Fatigue-Free", "Smooth Mids"),
            bassStrength = 700,
            virtStrength = 450,
            loudnessGain = 220,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(600, 350, 200, 150, 200)
        ),
        EngineMode(
            id = "bose_deep",
            engine = SoundEngine.BOSE_EQ,
            title = "Deep Bass Contour",
            description = "Sub-harmonic bass boost with dynamic active volume contouring",
            recommendationTag = "★ BEST FOR DEEP LOWS",
            traitChips = listOf("Active Contour", "+8.0dB Sub", "Rich Depth"),
            bassStrength = 900,
            virtStrength = 300,
            loudnessGain = 300,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(800, 450, 100, 200, 250)
        ),
        EngineMode(
            id = "bose_acoustic",
            engine = SoundEngine.BOSE_EQ,
            title = "Acoustic Clarity",
            description = "Crystal acoustic instrument separation and crisp front-stage vocal lift",
            recommendationTag = "★ BEST FOR VOCAL CLARITY",
            traitChips = listOf("String Separation", "+5.5dB Highs", "Clean Lows"),
            bassStrength = 350,
            virtStrength = 500,
            loudnessGain = 180,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(200, 150, 450, 550, 450)
        ),

        // 5. Sennheiser AMBEO 3D Modes
        EngineMode(
            id = "ambeo_boost",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO 3D Boost",
            description = "High-definition spherical sound field with rich spatial reverb",
            recommendationTag = "★ BEST FOR HOLOGRAPHIC 3D",
            traitChips = listOf("Holographic 3D", "+6.0dB Treble", "Micro-Reverb"),
            bassStrength = 500,
            virtStrength = 900,
            loudnessGain = 250,
            reverbPreset = PresetReverb.PRESET_MEDIUMROOM,
            bandGains = listOf(350, 200, 400, 600, 600)
        ),
        EngineMode(
            id = "ambeo_natural",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO Natural",
            description = "True holographic acoustic depth with uncolored natural room decay",
            recommendationTag = "★ BEST FOR NATURAL ACOUSTICS",
            traitChips = listOf("Uncolored", "Room Decay", "Audiophile Depth"),
            bassStrength = 350,
            virtStrength = 600,
            loudnessGain = 120,
            reverbPreset = PresetReverb.PRESET_SMALLROOM,
            bandGains = listOf(150, 100, 300, 450, 400)
        ),
        EngineMode(
            id = "ambeo_concert",
            engine = SoundEngine.SENNHEISER_AMBEO,
            title = "AMBEO Concert Hall",
            description = "Concert hall acoustic reflections with deep low-end resonance",
            recommendationTag = "★ BEST FOR WIDE STAGES",
            traitChips = listOf("Concert Hall", "+5.0dB Sub", "Natural Reverb"),
            bassStrength = 650,
            virtStrength = 950,
            loudnessGain = 280,
            reverbPreset = PresetReverb.PRESET_LARGEHALL,
            bandGains = listOf(500, 300, 200, 450, 550)
        ),

        // 6. Viper Dynamic Master Modes
        EngineMode(
            id = "viper_tube",
            engine = SoundEngine.VIPER_FX,
            title = "Vacuum Tube Warmth",
            description = "Analog tube harmonic saturation for a warm, vinyl-like vintage tone",
            recommendationTag = "★ BEST FOR VINTAGE ANALOG",
            traitChips = listOf("Analog Harmonics", "+6.5dB Lows", "Vintage Warmth"),
            bassStrength = 750,
            virtStrength = 600,
            loudnessGain = 300,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(650, 350, 250, 400, 550)
        ),
        EngineMode(
            id = "viper_exciter",
            engine = SoundEngine.VIPER_FX,
            title = "Sub-Harmonic Exciter",
            description = "Massive club subwoofer rumble with tight bass transients",
            recommendationTag = "★ BEST FOR BASSHEADS",
            traitChips = listOf("+9.5dB Sub", "Exciter DSP", "Max Slam"),
            bassStrength = 1000,
            virtStrength = 700,
            loudnessGain = 400,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(950, 500, 100, 450, 650)
        ),
        EngineMode(
            id = "viper_air",
            engine = SoundEngine.VIPER_FX,
            title = "Ultra Clarity & Air",
            description = "Crisp upper treble sparkle with widened stereo imaging",
            recommendationTag = "★ BEST FOR HIGHS & AIR",
            traitChips = listOf("+9.0dB Air", "Stereo Widening", "Ultra Sparkle"),
            bassStrength = 400,
            virtStrength = 750,
            loudnessGain = 220,
            reverbPreset = PresetReverb.PRESET_NONE,
            bandGains = listOf(200, 100, 300, 700, 900)
        ),

        // 7. Hi-Res Direct Modes
        EngineMode(
            id = "hires_direct",
            engine = SoundEngine.HI_RES,
            title = "Bit-Perfect Direct",
            description = "Pure uncompressed studio master audio with zero DSP coloration",
            recommendationTag = "★ AUDIOPHILE BIT-PERFECT",
            traitChips = listOf("Zero DSP", "Bit-Perfect", "100% Lossless"),
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
    val bassBoostStrength: Short = 650,
    val virtualizerStrength: Short = 750,
    val loudnessGainMb: Int = 220,
    val reverbPreset: Short = PresetReverb.PRESET_SMALLROOM,
    val bands: List<EqualizerBand> = listOf(
        EqualizerBand(0, 60, 500, -1500, 1500),
        EqualizerBand(1, 230, 200, -1500, 1500),
        EqualizerBand(2, 910, 0, -1500, 1500),
        EqualizerBand(3, 3600, 350, -1500, 1500),
        EqualizerBand(4, 14000, 450, -1500, 1500)
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
