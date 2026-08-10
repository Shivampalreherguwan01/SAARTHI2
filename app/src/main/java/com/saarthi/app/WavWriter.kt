package com.saarthi.app

import java.io.File
import java.io.FileOutputStream

object WavWriter {
    fun writeWav(file: File, audio: ShortArray, sampleRate: Int) {
        val byteRate = sampleRate * 2
        val dataSize = audio.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { out ->
            fun writeInt(v: Int) {
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
                out.write((v shr 16) and 0xff)
                out.write((v shr 24) and 0xff)
            }
            fun writeShort(v: Int) {
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
            }

            out.write("RIFF".toByteArray())
            writeInt(totalSize)
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            writeInt(16)
            writeShort(1)
            writeShort(1)
            writeInt(sampleRate)
            writeInt(byteRate)
            writeShort(2)
            writeShort(16)
            out.write("data".toByteArray())
            writeInt(dataSize)

            for (sample in audio) {
                writeShort(sample.toInt())
            }
        }
    }
}
