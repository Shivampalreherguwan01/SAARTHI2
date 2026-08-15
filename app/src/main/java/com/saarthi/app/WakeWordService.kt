package com.saarthi.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class WakeWordService : Service(), TextToSpeech.OnInitListener {

    companion object {
        @Volatile var isRunning = false
    }

    private val CHANNEL_ID = "saarthi_channel"
    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private var overlayAdded = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggerTime = 0L
    private var running = true
    private var tts: TextToSpeech? = null
    private var inCommandMode = false
    private var currentRecorder: AudioRecord? = null
    private var geminiClient: GeminiLiveClient? = null
    private var audioPlayer: AudioPlayer? = null
    private var sessionActive = false

    private val wakeWords = listOf(
        "saarthi", "sarthi", "saathi", "sathi",
        "सारथि", "सारथी", "सार्थी", "शारथी", "शारृथी", "साथी"
    )

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        tts = TextToSpeech(this, this)
        startForegroundServiceWithNotification()
        setupOverlay()
        thread { listenLoop() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }

    private fun textContainsWakeWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        val words = lower.split(" ", "\n", ",", ".", "!", "?").filter { it.isNotBlank() }
        for (word in words) {
            for (target in wakeWords) {
                val threshold = if (target.length <= 5) 1 else 2
                if (editDistance(word, target) <= threshold) return true
            }
        }
        return false
    }

    private fun listenLoop() {
        val sampleRate = VoicePrint.SAMPLE_RATE
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
        )

        val chunkSize = 800
        val chunk = ShortArray(chunkSize)
        val speechThreshold = 700.0
        val silenceChunksToEnd = 12
        val maxUtteranceSamples = sampleRate * 3

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(recorder.audioSessionId)
                aec?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(recorder.audioSessionId)
                ns?.enabled = true
            }
        } catch (e: Exception) {
        }

        recorder.startRecording()
        updateNotification("Sun raha hoon...")

        while (running) {
            if (inCommandMode) {
                Thread.sleep(200)
                continue
            }

            val n = recorder.read(chunk, 0, chunkSize)
            if (n <= 0) continue

            val chunkRms = VoicePrint.computeRMS(chunk.copyOf(n))
            if (chunkRms < speechThreshold) {
                continue
            }

            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < 1500) {
                continue
            }

            val collected = mutableListOf<Short>()
            for (i in 0 until n) collected.add(chunk[i])
            var silenceCount = 0

            while (collected.size < maxUtteranceSamples) {
                val n2 = recorder.read(chunk, 0, chunkSize)
                if (n2 <= 0) continue
                for (i in 0 until n2) collected.add(chunk[i])
                val rms2 = VoicePrint.computeRMS(chunk.copyOf(n2))
                if (rms2 < speechThreshold) {
                    silenceCount++
                    if (silenceCount >= silenceChunksToEnd) break
                } else {
                    silenceCount = 0
                }
            }

            if (collected.size < sampleRate / 3) continue

            val utterance = collected.toShortArray()
            try {
                val wavFile = File(cacheDir, "wakecheck.wav")
                WavWriter.writeWav(wavFile, utterance, sampleRate)
                val text = GroqApi.transcribe(wavFile)

                if (text != null && textContainsWakeWord(text)) {
                    lastTriggerTime = System.currentTimeMillis()
                    triggerWakeWord(recorder)
                }
            } catch (e: Exception) {
                updateNotification("Check error: ${e.message}")
            }
        }

        recorder.stop()
        recorder.release()
    }

    private fun triggerWakeWord(sharedRecorder: AudioRecord) {
        currentRecorder = sharedRecorder
        updateNotification("Connecting...")
        showActivatedAnimation()
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))

        inCommandMode = true
        sessionActive = true
        audioPlayer = AudioPlayer()
        audioPlayer?.start()

        val installedApps = CommandExecutor.getInstalledAppLabels(this)

        geminiClient = GeminiLiveClient(
            onAudioChunk = { bytes -> audioPlayer?.play(bytes) },
            onFunctionCall = { name, args, callId ->
                handleGeminiFunctionCall(name, args, callId)
            },
            onTurnComplete = {
                lastSpeechTime = System.currentTimeMillis()
            },
            onError = { err ->
                updateNotification("Gemini error: $err")
                endGeminiSession()
            },
            onOpen = {
                updateNotification("Ji, bataiye!")
            }
        )
        geminiClient?.connect(installedApps)

        thread { streamAudioToGemini(sharedRecorder) }
    }

    private var lastSpeechTime = System.currentTimeMillis()

    private fun streamAudioToGemini(recorder: AudioRecord) {
        val chunk = ShortArray(1600)
        lastSpeechTime = System.currentTimeMillis()
        val sessionStart = System.currentTimeMillis()

        while (sessionActive) {
            val n = recorder.read(chunk, 0, chunk.size)
            if (n > 0) {
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val s = chunk[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                geminiClient?.sendAudioChunk(bytes)

                val rms = VoicePrint.computeRMS(chunk.copyOf(n))
                if (rms > 700.0) lastSpeechTime = System.currentTimeMillis()
            }

            if (System.currentTimeMillis() - lastSpeechTime > 8000) {
                break
            }
            if (System.currentTimeMillis() - sessionStart > 120000) {
                break
            }
        }
        endGeminiSession()
    }

    private fun handleGeminiFunctionCall(name: String, args: JSONObject, callId: String) {
        when (name) {
            "open_app" -> {
                val appName = args.optString("app_name", "")
                val success = CommandExecutor.execute(this, appName)
                geminiClient?.sendFunctionResponse(name, callId, if (success) "opened" else "app not found")
            }
            "close_app" -> {
                CommandExecutor.goHome(this)
                geminiClient?.sendFunctionResponse(name, callId, "closed")
            }
            "search_web" -> {
                val query = args.optString("query", "")
                thread {
                    val result = GroqSearch.search(query) ?: "Search failed, no results found"
                    geminiClient?.sendFunctionResponse(name, callId, result)
                }
            }
            "end_session" -> {
                geminiClient?.sendFunctionResponse(name, callId, "ending")
                handler.postDelayed({ endGeminiSession() }, 1500)
            }
        }
    }

    private fun endGeminiSession() {
        sessionActive = false
        geminiClient?.close()
        audioPlayer?.stop()
        hideActivatedAnimation()
        updateNotification("Sun raha hoon...")
        lastTriggerTime = System.currentTimeMillis()
        inCommandMode = false
    }

    private fun captureFollowUpCommand(recorder: AudioRecord, timeoutMs: Long): ShortArray? {
        val sampleRate = VoicePrint.SAMPLE_RATE
        val chunkSize = 800
        val chunk = ShortArray(chunkSize)
        val speechThreshold = 700.0
        val silenceChunksToEnd = 12
        val maxUtteranceSamples = sampleRate * 6
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val n = recorder.read(chunk, 0, chunkSize)
            if (n <= 0) continue
            val rms = VoicePrint.computeRMS(chunk.copyOf(n))
            if (rms < speechThreshold) continue

            val collected = mutableListOf<Short>()
            for (i in 0 until n) collected.add(chunk[i])
            var silenceCount = 0

            while (collected.size < maxUtteranceSamples) {
                val n2 = recorder.read(chunk, 0, chunkSize)
                if (n2 <= 0) continue
                for (i in 0 until n2) collected.add(chunk[i])
                val rms2 = VoicePrint.computeRMS(chunk.copyOf(n2))
                if (rms2 < speechThreshold) {
                    silenceCount++
                    if (silenceCount >= silenceChunksToEnd) break
                } else {
                    silenceCount = 0
                }
            }

            if (collected.size >= sampleRate / 3) {
                return collected.toShortArray()
            }
        }
        return null
    }

    private fun setupOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = TextView(this).apply {
            text = "✨ Saarthi ✨"
            val bg = GradientDrawable()
            bg.cornerRadius = 40f
            bg.setColor(Color.parseColor("#CC1A1A2E"))
            bg.setStroke(3, Color.parseColor("#00E5FF"))
            background = bg
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            setPadding(40, 20, 40, 20)
            visibility = View.GONE
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        try {
            windowManager?.addView(overlayView, params)
            overlayAdded = true
        } catch (e: Exception) {
        }
    }

    private fun showActivatedAnimation() {
        if (!overlayAdded) return
        handler.post {
            overlayView?.visibility = View.VISIBLE
            val pulse = AlphaAnimation(0.3f, 1.0f)
            pulse.duration = 400
            pulse.repeatMode = AlphaAnimation.REVERSE
            pulse.repeatCount = 3
            overlayView?.startAnimation(pulse)
        }
    }

    private fun hideActivatedAnimation() {
        handler.post { overlayView?.visibility = View.GONE }
    }

    private fun startForegroundServiceWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Saarthi Listening", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saarthi")
            .setContentText("Shuru ho raha hai...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saarthi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1, notification)
    }

    override fun onDestroy() {
        isRunning = false
        running = false
        tts?.stop()
        tts?.shutdown()
        handler.removeCallbacksAndMessages(null)
        if (overlayAdded) {
            overlayView?.let { windowManager?.removeView(it) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
