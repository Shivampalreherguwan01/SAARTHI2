package com.saarthi.app

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object TemplateStore {
    fun save(context: Context, index: Int, features: FloatArray) {
        val file = File(context.filesDir, "template_$index.dat")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            for (v in features) raf.writeFloat(v)
        }
    }

    fun loadAll(context: Context): List<FloatArray> {
        val templates = mutableListOf<FloatArray>()
        for (i in 1..5) {
            val file = File(context.filesDir, "template_$i.dat")
            if (file.exists()) {
                RandomAccessFile(file, "r").use { raf ->
                    val count = (file.length() / 4).toInt()
                    val arr = FloatArray(count)
                    for (j in 0 until count) arr[j] = raf.readFloat()
                    templates.add(arr)
                }
            }
        }
        return templates
    }

    fun countSaved(context: Context): Int {
        var count = 0
        for (i in 1..5) {
            if (File(context.filesDir, "template_$i.dat").exists()) count++
        }
        return count
    }
}
