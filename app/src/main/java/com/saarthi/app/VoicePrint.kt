package com.saarthi.app

import kotlin.math.log10
import kotlin.math.sqrt

object VoicePrint {
    const val SAMPLE_RATE = 16000
    const val FRAME_SIZE = 320
    const val HOP_SIZE = 160
    const val FIXED_FRAMES = 40

    fun computeRMS(audio: ShortArray): Double {
        var sum = 0.0
        for (s in audio) sum += (s.toDouble() * s.toDouble())
        return sqrt(sum / audio.size)
    }

    fun extractFeatures(audio: ShortArray): FloatArray {
        val frames = mutableListOf<FloatArray>()
        var i = 0
        while (i + FRAME_SIZE <= audio.size) {
            var energy = 0.0
            var zeroCrossings = 0
            for (j in 0 until FRAME_SIZE) {
                val sample = audio[i + j].toDouble()
                energy += sample * sample
                if (j > 0 && ((audio[i + j] >= 0) != (audio[i + j - 1] >= 0))) {
                    zeroCrossings++
                }
            }
            val logEnergy = log10(energy / FRAME_SIZE + 1.0).toFloat()
            val zcr = (zeroCrossings.toFloat() / FRAME_SIZE)
            frames.add(floatArrayOf(logEnergy, zcr))
            i += HOP_SIZE
        }

        if (frames.isEmpty()) return FloatArray(FIXED_FRAMES * 2)

        val result = FloatArray(FIXED_FRAMES * 2)
        for (f in 0 until FIXED_FRAMES) {
            val srcIndex = (f * frames.size / FIXED_FRAMES).coerceIn(0, frames.size - 1)
            result[f * 2] = frames[srcIndex][0]
            result[f * 2 + 1] = frames[srcIndex][1]
        }

        val norm = sqrt(result.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (k in result.indices) result[k] = result[k] / norm
        }
        return result
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }
}
