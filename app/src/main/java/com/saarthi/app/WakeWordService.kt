package com.saarthi.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.core.app.NotificationCompat

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val CHANNEL_ID = "saarthi_channel"

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private var overlayAdded = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        setupOverlay()
        handler.postDelayed({ startListening() }, 500)
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

    private fun startListening() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                updateNotification("Google recognizer available nahi hai")
                return
            }

            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    handleHeard(results)
                    handler.postDelayed({ startListening() }, 300)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    handleHeard(partialResults)
                }
                override fun onError(error: Int) {
                    updateNotification("Error code: $error")
                    handler.postDelayed({ startListening() }, 1000)
                }
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("Sun raha hoon...")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            updateNotification("Exception: ${e.message}")
            handler.postDelayed({ startListening() }, 1000)
        }
    }

    private fun handleHeard(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.get(0)?.lowercase() ?: return
        if (text.isBlank()) return

        updateNotification("Suna: $text")

        if (text.contains("saarthi") || text.contains("sarthi") || text.contains("sarathi") || text.contains("सारथी")) {
            showActivatedAnimation()
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
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
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
        if (overlayAdded) {
            overlayView?.let { windowManager?.removeView(it) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
