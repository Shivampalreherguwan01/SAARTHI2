package com.saarthi.app

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object TemplateStore {
    fun save(context: Context, features: FloatArray) {
        val timestamp = System.currentTimeMillis()
        val file = File(context.filesDir, "template_$timestamp.dat")
        RandomAccessFile(file, "rw").use { raf ->
            for (v in features) raf.writeFloat(v)
        }
    }

    fun loadAll(context: Context): List<FloatArray> {
        val templates = mutableListOf<FloatArray>()
        val files = context.filesDir.listFiles { f -> f.name.startsWith("template_") && f.name.endsWith(".dat") }
        files?.forEach { file ->
            RandomAccessFile(file, "r").use { raf ->
                val count = (file.length() / 4).toInt()
                val arr = FloatArray(count)
                for (j in 0 until count) arr[j] = raf.readFloat()
                templates.add(arr)
            }
        }
        return templates
    }

    fun countSaved(context: Context): Int {
        val files = context.filesDir.listFiles { f -> f.name.startsWith("template_") && f.name.endsWith(".dat") }
        return files?.size ?: 0
    }

    fun clearAll(context: Context) {
        val files = context.filesDir.listFiles { f -> f.name.startsWith("template_") && f.name.endsWith(".dat") }
        files?.forEach { it.delete() }
    }
}
