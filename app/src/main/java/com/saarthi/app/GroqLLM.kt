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

    fun interpret(userText: String, installedApps: List<String> = emptyList()): ActionResult? {
        try {
            val appsListStr = installedApps.joinToString(", ")
            val systemPrompt = """
You are Saarthi, a voice assistant living inside an Android app. The user speaks in Hindi, English, or a mix of both (and sometimes regional dialects). Understand their intent regardless of language or phrasing.

Here is the EXACT and COMPLETE list of apps installed on this phone (comma separated): $appsListStr

STRICT RULES:
1. If the user wants to OPEN an app, and an app matching their request EXISTS in the list above (exact or very close spelling match), respond with action "open_app" and target must be copied EXACTLY character-for-character from the list.
2. If the user wants to open an app but NO matching app exists in the list, DO NOT guess or pick a random unrelated app. Instead respond with action "reply_only" and tell them in the reply that this app is not installed.
3. ANY phrase meaning close, band karo, band kar do, stop, exit, wapas jao, hato, minimize, ya home jao — for ANY app — MUST always be action "close_app", regardless of whether the app name is recognized. target can be the app name they mentioned or null.
4. Never invent or hallucinate an app name that is not in the list.

Respond ONLY with JSON in one of these exact formats, nothing else:
{"action": "open_app", "target": "<EXACT app name copied from the list>", "reply": "<short spoken confirmation in Hindi>"}
{"action": "close_app", "target": "<app name mentioned, or null>", "reply": "<short spoken confirmation in Hindi, e.g. 'band kar raha hoon'>"}
{"action": "reply_only", "target": null, "reply": "<your natural spoken answer in Hindi, matching the user's language style>"}
{"action": "unknown", "target": null, "reply": "Mujhe samajh nahi aaya, aap kya karna chahte hain? Kripya thoda aur bataiye."}

Always respond with valid JSON only, no extra text.
            """.trimIndent()

            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.put(JSONObject().put("role", "user").put("content", userText))

            val body = JSONObject()
            body.put("model", "llama-3.1-8b-instant")
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
