package com.missingcore.music.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

enum class HapticIntensity(val amplitude: Int, val description: String) {
    OFF(0, "Disabled"),
    SUBTLE(80, "Subtle Ticks"),
    BALANCED(160, "Balanced Beats"),
    HEAVY(255, "Deep Bass Pulse")
}

class BeatHapticManager(private val context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.e("BeatHapticManager", "Error initializing vibrator: ${e.message}")
        null
    }

    private var isHapticActive = false
    private var intensity = HapticIntensity.OFF

    fun isEnabled(): Boolean = isHapticActive && intensity != HapticIntensity.OFF

    fun setIntensity(newIntensity: HapticIntensity) {
        intensity = newIntensity
        isHapticActive = newIntensity != HapticIntensity.OFF
    }

    fun getIntensity(): HapticIntensity = intensity

    // 1. LOW BEAT TRIGGER (Kick Drums & Sub-Bass Drops) - Deep, resonant heavy pulse
    fun triggerLowBeat() {
        if (!isEnabled() || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (intensity) {
                    HapticIntensity.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticIntensity.BALANCED -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticIntensity.SUBTLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticIntensity.OFF -> return
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (intensity) {
                    HapticIntensity.HEAVY -> 18L
                    HapticIntensity.BALANCED -> 14L
                    HapticIntensity.SUBTLE -> 10L
                    HapticIntensity.OFF -> return
                }
                val effect = VibrationEffect.createOneShot(duration, intensity.amplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(14L)
            }
        } catch (e: Exception) {
            Log.w("BeatHapticManager", "Low beat vibrate failed: ${e.message}")
        }
    }

    // 2. HIGH BEAT TRIGGER (Snares, Claps, Hi-Hats & Crisp Transients) - Sharp, distinct micro-click
    fun triggerHighBeat() {
        if (!isEnabled() || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (intensity) {
                    HapticIntensity.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticIntensity.BALANCED -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticIntensity.SUBTLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticIntensity.OFF -> return
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (intensity) {
                    HapticIntensity.HEAVY -> 8L
                    HapticIntensity.BALANCED -> 6L
                    HapticIntensity.SUBTLE -> 4L
                    HapticIntensity.OFF -> return
                }
                val amp = (intensity.amplitude * 0.65f).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(duration, amp)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(6L)
            }
        } catch (e: Exception) {
            Log.w("BeatHapticManager", "High beat vibrate failed: ${e.message}")
        }
    }

    // Backward-compatible triggerBeat method delegating to triggerLowBeat
    fun triggerBeat() {
        triggerLowBeat()
    }

    // UI Micro-Haptic Triggers
    fun vibrateClick() {
        performVibration(durationMs = 10, amplitude = 90)
    }

    fun vibratePlayPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            } catch (_: Exception) {}
        }
        performVibration(durationMs = 18, amplitude = 160)
    }

    fun vibrateFavorite() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val timings = longArrayOf(0, 16, 45, 20)
                val amplitudes = intArrayOf(0, 120, 0, 200)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                return
            } catch (_: Exception) {}
        }
        performVibration(durationMs = 24, amplitude = 180)
    }

    fun vibrateSeekTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) {}
        }
        performVibration(durationMs = 6, amplitude = 50)
    }

    private fun performVibration(durationMs: Long, amplitude: Int) {
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val clampedAmp = amplitude.coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(durationMs, clampedAmp)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w("BeatHapticManager", "Vibrate failed: ${e.message}")
        }
    }
}
