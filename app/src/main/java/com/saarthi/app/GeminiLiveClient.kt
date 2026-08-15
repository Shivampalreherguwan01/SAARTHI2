package com.saarthi.app

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onFunctionCall: (name: String, args: JSONObject, callId: String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onError: (String) -> Unit,
    private val onOpen: () -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var setupDone = false

    fun connect(installedApps: List<String>) {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendSetup(ws, installedApps)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val bodyStr = try { response?.body?.string() } catch (e: Exception) { null }
                onError("Connection failed: ${t.message} | ${response?.code} | $bodyStr")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            }
        })
    }

    private fun sendSetup(ws: WebSocket, installedApps: List<String>) {
        val appsListStr = installedApps.joinToString(", ")
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-3.1-flash-live-preview")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                })
                put("realtimeInputConfig", JSONObject().apply {
                    put("automaticActivityDetection", JSONObject().apply {
                        put("disabled", false)
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text",
                        "You are Saarthi, a helpful voice assistant on the user's Android phone. " +
                        "The user speaks Hindi, English, or a mix of both, and sometimes regional dialects. " +
                        "Understand their intent regardless of language or phrasing, and reply naturally in the same style. " +
                        "Here is the exact list of apps installed on this phone: $appsListStr. " +
                        "If the user wants to open an app, call the open_app function with the exact app name from the list (match tolerantly for spelling/pronunciation mistakes). " +
                        "If the user wants to close/exit/go back from an app, call close_app. " +
                        "If the user says something like 'band karo', 'ruk jao', 'bye', or otherwise wants to end the conversation, call end_session. " +
                        "For general questions, use Google Search when the answer requires current/live information (weather, news, scores, prices, etc). For general knowledge questions, answer naturally and conversationally. Keep spoken replies concise."
                    )))
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "open_app")
                            put("description", "Open an app on the phone")
                            put("parameters", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("app_name", JSONObject().put("type", "string"))
                                })
                                put("required", JSONArray().put("app_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "close_app")
                            put("description", "Close current app / go to home screen")
                            put("parameters", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject())
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "search_web")
                            put("description", "Search the internet for current/live information like weather, news, scores, prices, or any fact you are not sure about")
                            put("parameters", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().put("type", "string"))
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "end_session")
                            put("description", "End the conversation when user wants to stop talking")
                            put("parameters", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject())
                            })
                        })
                    })
                    })
                })
            })
        }
        ws.send(setup.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("setupComplete")) {
                setupDone = true
                onOpen()
                return
            }
            if (json.has("toolCall")) {
                val calls = json.getJSONObject("toolCall").getJSONArray("functionCalls")
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val name = call.getString("name")
                    val args = call.optJSONObject("args") ?: JSONObject()
                    val callId = call.optString("id", "")
                    onFunctionCall(name, args, callId)
                }
                return
            }
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val parts = serverContent.getJSONObject("modelTurn").getJSONArray("parts")
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("inlineData")) {
                            val b64 = part.getJSONObject("inlineData").getString("data")
                            val audioBytes = Base64.decode(b64, Base64.DEFAULT)
                            onAudioChunk(audioBytes)
                        }
                    }
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                    onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiLive", "parse error: ${e.message}")
            if (text.contains("error", ignoreCase = true)) {
                onError("Server said: ${text.take(200)}")
            }
        }
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (!setupDone) return
        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val msg = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", b64)
                })
            })
        }
        webSocket?.send(msg.toString())
    }

    fun sendFunctionResponse(name: String, callId: String, result: String) {
        val msg = JSONObject().apply {
            put("toolResponse", JSONObject().apply {
                put("functionResponses", JSONArray().put(JSONObject().apply {
                    put("id", callId)
                    put("name", name)
                    put("response", JSONObject().put("result", result))
                }))
            })
        }
        webSocket?.send(msg.toString())
    }

    fun close() {
        webSocket?.close(1000, "done")
        setupDone = false
    }
}
