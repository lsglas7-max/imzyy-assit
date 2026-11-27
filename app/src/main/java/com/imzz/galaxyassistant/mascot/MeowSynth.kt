package com.imzz.galaxyassistant.mascot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Suara kucing buatan sendiri (disintesis di memori, tanpa file audio, tanpa API, tanpa internet).
 * - meow(): "mi-yaaw" dengan kontur nada naik-turun + vibrato.
 * - purr(): dengkuran halus saat kepala Skylen diusap.
 */
object MeowSynth {
    private const val RATE = 22050
    @Volatile private var busyUntil = 0L

    fun meow(pitch: Float = 1f) {
        play(buildMeow(pitch.coerceIn(0.7f, 1.5f)), 650L)
    }

    fun purr() {
        play(buildPurr(), 1650L)
    }

    private fun play(samples: ShortArray, durationMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (now < busyUntil) return
        busyUntil = now + durationMs
        Thread {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(durationMs + 120L)
            } catch (e: Exception) {
                // Audio gagal diputar: Skylen tetap jalan tanpa suara.
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }.start()
    }

    private fun buildMeow(pitch: Float): ShortArray {
        val dur = 0.58f
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val p = i / n.toFloat()
            // kontur nada: naik cepat, lalu turun ("mi-yaaw")
            val contour = sin(PI.toFloat() * p).toDouble().pow(1.2)
            var f = (380.0 + 520.0 * contour) * (1.0 - 0.22 * p) * pitch
            f *= 1.0 + 0.015 * sin(2.0 * PI * 6.0 * t)
            phase += 2.0 * PI * f / RATE
            // "vokal" bergerak: harmonik atas menguat di tengah
            val m = sin(PI * p)
            val w2 = 0.3 + 0.7 * m
            val w3 = 0.15 + 0.5 * m
            val s = sin(phase) + 0.55 * w2 * sin(2 * phase) + 0.32 * w3 * sin(3 * phase) +
                0.18 * sin(4 * phase) + 0.08 * sin(5 * phase)
            val attack = min(1.0, p / 0.06)
            val release = if (p < 0.78) 1.0 else (1.0 - p) / 0.22
            val env = attack * release * (0.75 + 0.25 * m)
            val value = s / 2.0 * env * 0.45
            out[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildPurr(): ShortArray {
        val dur = 1.55f
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        var low = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val p = i / n.toFloat()
            val noise = Random.nextDouble(-1.0, 1.0)
            low += 0.07 * (noise - low)
            val pulse = 0.55 + 0.45 * sin(2.0 * PI * 25.0 * t)
            val env = min(1.0, p / 0.12) * (if (p > 0.8) (1.0 - p) / 0.2 else 1.0)
            val value = (low * 4.0).coerceIn(-1.0, 1.0) * pulse * env * 0.5
            out[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        // sedikit bunyi "rrr" bernada rendah agar terdengar di speaker kecil
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val extra = 0.12 * sin(2.0 * PI * 55.0 * t) * (0.55 + 0.45 * sin(2.0 * PI * 25.0 * t))
            val mixed = out[i] + (extra * Short.MAX_VALUE * (1.0 - abs(2.0 * i / n - 1.0) * 0.3)).toInt()
            out[i] = mixed.coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
