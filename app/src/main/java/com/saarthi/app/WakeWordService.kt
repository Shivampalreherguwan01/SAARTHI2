package com.saarthi.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class WakeWordService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val CHANNEL_ID = "saarthi_channel"

    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null

    private val grammar = "[\"saarthi\", \"sarthi\", \"sarathi\", \"[unk]\"]"

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        addOverlayIndicator()
        thread {
            try {
                val modelDir = File(filesDir, "model")
                if (!modelDir.exists()) {
                    updateNotification("Model copy ho raha hai...")
                    copyAssetFolder("model", modelDir.absolutePath)
                }
                model = Model(modelDir.absolutePath)
                startListening()
            } catch (e: Exception) {
                updateNotification("Model load error: ${e.message}")
            }
        }
    }

    private fun addOverlayIndicator() {
        if (!android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = TextView(this).apply {
            text = "  🎤 Saarthi  "
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            setPadding(16, 8, 16, 8)
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
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 10
        params.y = 60

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
        }
    }

    private fun setOverlayState(active: Boolean) {
        overlayView?.post {
            if (active) {
                overlayView?.text = "  🎤 SAARTHI  "
                overlayView?.setTextColor(Color.parseColor("#00FF00"))
            } else {
                overlayView?.text = "  🎤 Saarthi  "
                overlayView?.setTextColor(Color.parseColor("#00E5FF"))
            }
        }
    }

    private fun copyAssetFolder(srcPath: String, dstPath: String) {
        val files = assets.list(srcPath) ?: return
        File(dstPath).mkdirs()
        for (fileName in files) {
            val srcFilePath = "$srcPath/$fileName"
            val dstFilePath = "$dstPath/$fileName"
            val subFiles = assets.list(srcFilePath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(srcFilePath, dstFilePath)
            } else {
                assets.open(srcFilePath).use { input ->
                    FileOutputStream(dstFilePath).use { output ->
                        input.copyTo(output)
                    }
                }
            }
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
            val rec = Recognizer(model, 16000.0f, grammar)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            updateNotification("Sun raha hoon...")
        } catch (e: Exception) {
            updateNotification("Listen error: ${e.message}")
        }
    }

    override fun onResult(hypothesis: String?) {
        checkForWakeWord(hypothesis)
    }

    override fun onPartialResult(hypothesis: String?) {
        checkForWakeWord(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        checkForWakeWord(hypothesis)
    }

    private fun checkForWakeWord(hypothesis: String?) {
        if (hypothesis == null) return
        try {
            val json = JSONObject(hypothesis)
            val text = (json.optString("text", "") + " " + json.optString("partial", "")).lowercase()
            if (text.contains("saarthi") || text.contains("sarthi") || text.contains("sarathi")) {
                updateNotification("Ji, bataiye!")
                setOverlayState(true)
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
        }
    }

    override fun onError(exception: Exception?) {
        updateNotification("Error: ${exception?.message}")
    }

    override fun onTimeout() {
        speechService?.startListening(this)
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
        speechService?.stop()
        speechService?.shutdown()
        overlayView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
