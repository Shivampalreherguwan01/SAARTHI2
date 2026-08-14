package com.saarthi.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class AudioPlayer {
    private var audioTrack: AudioTrack? = null

    fun start() {
        val sampleRate = 24000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize * 4,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }

    fun play(pcmData: ByteArray) {
        audioTrack?.write(pcmData, 0, pcmData.size)
    }

    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
        }
        audioTrack = null
    }
}
