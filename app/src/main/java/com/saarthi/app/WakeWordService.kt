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
        updateNotification("Ji, bataiye!")
        showActivatedAnimation()
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))

        inCommandMode = true
        thread {
            try {
                var keepGoing = true
                while (keepGoing) {
                    val commandAudio = captureFollowUpCommand(sharedRecorder, 8000)
                    if (commandAudio == null) {
                        keepGoing = false
                        break
                    }

                    updateNotification("Samajh raha hoon...")
                    val wavFile = File(cacheDir, "command.wav")
                    WavWriter.writeWav(wavFile, commandAudio, VoicePrint.SAMPLE_RATE)
                    val text = GroqApi.transcribe(wavFile)

                    if (text.isNullOrBlank()) {
                        updateNotification("Kuch samajh nahi aaya")
                        continue
                    }

                    val lowerText = text.lowercase()
                    if (lowerText.contains("band karo") || lowerText.contains("band ho jao") ||
                        lowerText.contains("stop") || lowerText.contains("बंद करो") || lowerText.contains("रुक")) {
                        tts?.speak("Theek hai", TextToSpeech.QUEUE_FLUSH, null, null)
                        keepGoing = false
                        break
                    }

                    handleCommand(text)
                }
            } catch (e: Exception) {
                updateNotification("Command error: ${e.message}")
                Thread.sleep(2000)
            }
            hideActivatedAnimation()
            updateNotification("Sun raha hoon...")
            lastTriggerTime = System.currentTimeMillis()
            inCommandMode = false
        }
    }

    private fun handleCommand(text: String) {
        val learned = LearnedCommands.lookup(this, text)
        val interpretation = learned ?: GroqLLM.interpret(text)

        if (interpretation == null) {
            updateNotification("Aapne kaha: $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }

        updateNotification(interpretation.reply)
        tts?.speak(interpretation.reply, TextToSpeech.QUEUE_FLUSH, null, null)

        when (interpretation.actionType) {
            "open_app" -> {
                if (interpretation.target != null) {
                    CommandExecutor.execute(this, interpretation.target)
                    if (learned == null) {
                        LearnedCommands.save(this, text, interpretation.actionType, interpretation.target, interpretation.reply)
                    }
                }
            }
            "close_app" -> {
                CommandExecutor.goHome(this)
                if (learned == null) {
                    LearnedCommands.save(this, text, interpretation.actionType, interpretation.target, interpretation.reply)
                }
            }
            "unknown" -> {
                Thread.sleep(800)
                val clarification = captureFollowUpCommand(currentRecorder!!, 8000)
                if (clarification != null) {
                    val wavFile = File(cacheDir, "clarify.wav")
                    WavWriter.writeWav(wavFile, clarification, VoicePrint.SAMPLE_RATE)
                    val clarText = GroqApi.transcribe(wavFile)
                    if (!clarText.isNullOrBlank()) {
                        val combined = "User originally said: \"$text\". When asked to clarify, they said: \"$clarText\". Determine the action now."
                        val retryInterpretation = GroqLLM.interpret(combined)
                        if (retryInterpretation != null && retryInterpretation.actionType != "unknown") {
                            updateNotification(retryInterpretation.reply)
                            tts?.speak(retryInterpretation.reply, TextToSpeech.QUEUE_FLUSH, null, null)
                            if (retryInterpretation.actionType == "open_app" && retryInterpretation.target != null) {
                                CommandExecutor.execute(this, retryInterpretation.target)
                            } else if (retryInterpretation.actionType == "close_app") {
                                CommandExecutor.goHome(this)
                            }
                            LearnedCommands.save(this, text, retryInterpretation.actionType, retryInterpretation.target, retryInterpretation.reply)
                        }
                    }
                }
            }
        }
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
            CHANNEL_ID, "Saarthi Listening", NotificationManager.IMPORTANCE_HIGH
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
