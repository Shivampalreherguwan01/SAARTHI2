package com.saarthi.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GroqLLM {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ActionResult(val actionType: String, val target: String?, val reply: String)

    fun interpret(userText: String): ActionResult? {
        try {
            val systemPrompt = """
You are Saarthi, a voice assistant living inside an Android app. The user speaks in Hindi, English, or a mix of both (and sometimes regional dialects). Understand their intent regardless of language or phrasing.

Given what the user said, decide what action to take. Respond ONLY with JSON in this exact format, nothing else:
{"action": "open_app", "target": "<app name in english, e.g. camera, whatsapp, youtube, chrome, gallery, settings, gmail, maps, phone, messages>", "reply": "<short spoken confirmation in Hindi, e.g. 'camera khol raha hoon'>"}

OR if it's just a question/conversation with no app to open:
{"action": "reply_only", "target": null, "reply": "<your natural spoken answer in Hindi, matching the user's language style>"}

Always respond with valid JSON only, no extra text.
            """.trimIndent()

            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.put(JSONObject().put("role", "user").put("content", userText))

            val body = JSONObject()
            body.put("model", "llama-3.3-70b-versatile")
            body.put("messages", messages)
            body.put("temperature", 0.3)

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                val json = JSONObject(bodyStr)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val cleanContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val resultJson = JSONObject(cleanContent)
                val action = resultJson.optString("action", "reply_only")
                val target = resultJson.optString("target", null)
                val reply = resultJson.optString("reply", "")
                return ActionResult(action, target, reply)
            }
        } catch (e: Exception) {
            return null
        }
    }
}
