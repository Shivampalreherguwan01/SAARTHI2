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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class WakeWordService : Service() {

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

    private val THRESHOLD = 0.45f

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundServiceWithNotification()
        setupOverlay()
        val templates = TemplateStore.loadAll(this)
        if (templates.isEmpty()) {
            updateNotification("Pehle 'Saarthi Train Karo' karein")
            return
        }
        thread { listenLoop(templates) }
    }

    private fun listenLoop(templates: List<FloatArray>) {
        val sampleRate = VoicePrint.SAMPLE_RATE
        val windowSamples = sampleRate * 2
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
        )

        val ringBuffer = ShortArray(windowSamples)
        var writePos = 0
        var filled = 0

        val readChunk = ShortArray(1600)

        recorder.startRecording()
        updateNotification("Sun raha hoon... (0.00)")

        while (running) {
            val n = recorder.read(readChunk, 0, readChunk.size)
            if (n > 0) {
                for (i in 0 until n) {
                    ringBuffer[writePos] = readChunk[i]
                    writePos = (writePos + 1) % windowSamples
                    if (filled < windowSamples) filled++
                }

                if (filled >= windowSamples) {
                    val ordered = ShortArray(windowSamples)
                    for (i in 0 until windowSamples) {
                        ordered[i] = ringBuffer[(writePos + i) % windowSamples]
                    }
                    val features = VoicePrint.extractFeatures(ordered)
                    var minDist = Float.MAX_VALUE
                    for (t in templates) {
                        val d = VoicePrint.distance(features, t)
                        if (d < minDist) minDist = d
                    }

                    val distStr = String.format("%.2f", minDist)
                    updateNotification("Sun raha hoon... ($distStr)")

                    if (minDist < THRESHOLD) {
                        val now = System.currentTimeMillis()
                        if (now - lastTriggerTime > 2500) {
                            lastTriggerTime = now
                            updateNotification("Ji, bataiye! ($distStr)")
                            showActivatedAnimation()
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            }
            Thread.sleep(300)
        }

        recorder.stop()
        recorder.release()
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
            handler.postDelayed({ overlayView?.visibility = View.GONE }, 3000)
        }
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
        handler.removeCallbacksAndMessages(null)
        if (overlayAdded) {
            overlayView?.let { windowManager?.removeView(it) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
