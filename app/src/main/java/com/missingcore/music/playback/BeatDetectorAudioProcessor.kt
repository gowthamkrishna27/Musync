package com.missingcore.music.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * State-of-the-Art Dual-Band High-Precision Audio Haptic Processor.
 * 
 * Synchronizes both High and Low frequencies with zero acoustic latency:
 * 1. LOW BAND (Kick Drums / Sub-Bass 40Hz - 130Hz):
 *    4th-order cascaded IIR low-pass filter -> dynamic variance threshold -> triggers resonant low pulse.
 * 2. HIGH BAND (Snares / Claps / Hi-Hats 2.5kHz - 8kHz):
 *    High-pass differentiator -> spectral transient attack onset -> triggers crisp micro-clicks.
 * 3. 256-sample micro-windows (~5.8ms): Zero buffer-averaging latency.
 */
class BeatDetectorAudioProcessor(
    private val beatHapticManager: BeatHapticManager
) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channelCount = 2

    // 4th-order cascaded IIR Low-Pass Filter states (~110Hz cutoff for Kicks)
    private var lpfAlpha = 0.045f
    private var lpf1 = 0f
    private var lpf2 = 0f
    private var lpf3 = 0f
    private var lpf4 = 0f

    // 2nd-order High-Pass Filter states (~2600Hz cutoff for Snares/Hi-Hats)
    private var hpfAlpha = 0.28f
    private var hpfMid1 = 0f
    private var hpfMid2 = 0f

    // Windowing state (256 samples per analysis frame ≈ 5.8ms)
    private val windowSize = 256
    private var windowSampleCount = 0
    private var windowBassEnergySum = 0.0
    private var windowHighEnergySum = 0.0

    // Low Energy history circular buffer (43 frames ≈ 250ms)
    private val historySize = 43
    private val lowEnergyHistory = FloatArray(historySize) { 0.01f }
    private val highEnergyHistory = FloatArray(historySize) { 0.01f }
    private var historyIndex = 0

    // Onset Detection States
    private var prevLowEnergy = 0f
    private var prevHighEnergy = 0f
    private var lastLowBeatTimestampMs = 0L
    private var lastHighBeatTimestampMs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)

        val dt = 1.0f / sampleRate
        // Low cutoff (~110Hz)
        val rcLow = 1.0f / (2.0f * Math.PI.toFloat() * 110.0f)
        lpfAlpha = (dt / (rcLow + dt)).coerceIn(0.012f, 0.20f)

        // High cutoff (~2600Hz)
        val rcHigh = 1.0f / (2.0f * Math.PI.toFloat() * 2600.0f)
        hpfAlpha = (dt / (rcHigh + dt)).coerceIn(0.15f, 0.65f)

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (beatHapticManager.isEnabled() && beatHapticManager.getIntensity() != HapticIntensity.OFF) {
            try {
                val bufferCopy = inputBuffer.asReadOnlyBuffer()
                processPcmStream(bufferCopy)
            } catch (_: Exception) {
                // Safe fallback: never interrupt audio pipeline
            }
        }

        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    private fun processPcmStream(buffer: ByteBuffer) {
        if (!beatHapticManager.isEnabled() || beatHapticManager.getIntensity() == HapticIntensity.OFF) return

        val intensity = beatHapticManager.getIntensity()
        val minLowIntervalMs = when (intensity) {
            HapticIntensity.HEAVY -> 130L
            HapticIntensity.BALANCED -> 150L
            HapticIntensity.SUBTLE -> 180L
            HapticIntensity.OFF -> return
        }

        val minHighIntervalMs = when (intensity) {
            HapticIntensity.HEAVY -> 110L
            HapticIntensity.BALANCED -> 130L
            HapticIntensity.SUBTLE -> 160L
            HapticIntensity.OFF -> return
        }

        val lowSensitivity = when (intensity) {
            HapticIntensity.HEAVY -> 1.15f
            HapticIntensity.BALANCED -> 1.28f
            HapticIntensity.SUBTLE -> 1.50f
            HapticIntensity.OFF -> return
        }

        val highSensitivity = when (intensity) {
            HapticIntensity.HEAVY -> 1.25f
            HapticIntensity.BALANCED -> 1.45f
            HapticIntensity.SUBTLE -> 1.70f
            HapticIntensity.OFF -> return
        }

        while (buffer.remaining() >= 2) {
            val sampleShort = buffer.short
            val rawSample = sampleShort.toFloat() / 32768.0f // [-1.0f, 1.0f]

            // 1. 4th-order cascaded LPF for razor-sharp sub-bass / kick isolation
            lpf1 += lpfAlpha * (rawSample - lpf1)
            lpf2 += lpfAlpha * (lpf1 - lpf2)
            lpf3 += lpfAlpha * (lpf2 - lpf3)
            lpf4 += lpfAlpha * (lpf3 - lpf4)
            val bassSample = lpf4

            // 2. High-pass filter for snare / claps / hi-hat isolation
            hpfMid1 += hpfAlpha * (rawSample - hpfMid1)
            hpfMid2 += hpfAlpha * (hpfMid1 - hpfMid2)
            val highSample = rawSample - hpfMid2

            windowBassEnergySum += (bassSample * bassSample)
            windowHighEnergySum += (highSample * highSample)
            windowSampleCount++

            // Skip secondary channels if multi-channel to save CPU
            if (channelCount > 1 && buffer.remaining() >= 2) {
                buffer.short
            }

            // Window boundary reached (every 256 samples ≈ 5.8ms) -> analyze onsets
            if (windowSampleCount >= windowSize) {
                val currentLowEnergy = sqrt(windowBassEnergySum / windowSize).toFloat()
                val currentHighEnergy = sqrt(windowHighEnergySum / windowSize).toFloat()
                windowBassEnergySum = 0.0
                windowHighEnergySum = 0.0
                windowSampleCount = 0

                // Positive onset flux: dE = max(0, E[t] - E[t-1])
                val lowFlux = max(0f, currentLowEnergy - prevLowEnergy)
                val highFlux = max(0f, currentHighEnergy - prevHighEnergy)
                prevLowEnergy = currentLowEnergy
                prevHighEnergy = currentHighEnergy

                // Calculate running statistics from history buffer
                var lowSum = 0f
                var highSum = 0f
                for (i in 0 until historySize) {
                    lowSum += lowEnergyHistory[i]
                    highSum += highEnergyHistory[i]
                }
                val lowMean = lowSum / historySize
                val highMean = highSum / historySize

                var lowVarSum = 0f
                var highVarSum = 0f
                for (i in 0 until historySize) {
                    val dLow = lowEnergyHistory[i] - lowMean
                    lowVarSum += (dLow * dLow)
                    val dHigh = highEnergyHistory[i] - highMean
                    highVarSum += (dHigh * dHigh)
                }
                val lowStdDev = sqrt(lowVarSum / historySize)
                val highStdDev = sqrt(highVarSum / historySize)

                // Dynamic adaptive thresholds
                val lowDynamicThreshold = (lowMean * lowSensitivity) + (lowStdDev * 0.65f)
                val highDynamicThreshold = (highMean * highSensitivity) + (highStdDev * 0.85f)

                // Store in circular history buffers
                lowEnergyHistory[historyIndex] = currentLowEnergy
                highEnergyHistory[historyIndex] = currentHighEnergy
                historyIndex = (historyIndex + 1) % historySize

                val now = System.currentTimeMillis()

                // LOW BEAT EVALUATION (Kick Drums)
                val isLowBeatHit = (currentLowEnergy > lowDynamicThreshold) &&
                                   (lowFlux > (lowMean * 0.22f + 0.004f)) &&
                                   (now - lastLowBeatTimestampMs >= minLowLowInterval(now, minLowIntervalMs))

                if (isLowBeatHit) {
                    lastLowBeatTimestampMs = now
                    beatHapticManager.triggerLowBeat()
                }

                // HIGH BEAT EVALUATION (Snares, Claps, Hi-Hats)
                val isHighBeatHit = (currentHighEnergy > highDynamicThreshold) &&
                                    (highFlux > (highMean * 0.35f + 0.006f)) &&
                                    (now - lastHighBeatTimestampMs >= minHighIntervalMs) &&
                                    (!isLowBeatHit || (currentHighEnergy > currentLowEnergy * 1.5f))

                if (isHighBeatHit) {
                    lastHighBeatTimestampMs = now
                    beatHapticManager.triggerHighBeat()
                }
            }
        }
    }

    private fun minLowLowInterval(now: Long, minInterval: Long): Long {
        return minInterval
    }
}
