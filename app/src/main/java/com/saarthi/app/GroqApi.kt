package com.saarthi.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GroqApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File): String? {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                val json = JSONObject(body)
                return json.optString("text", null)
            }
        } catch (e: Exception) {
            return null
        }
    }
}
