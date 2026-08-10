package com.saarthi.app

import kotlin.math.sqrt

object VoicePrint {
    const val SAMPLE_RATE = 16000

    fun computeRMS(audio: ShortArray): Double {
        var sum = 0.0
        for (s in audio) sum += (s.toDouble() * s.toDouble())
        return sqrt(sum / audio.size)
    }
}
