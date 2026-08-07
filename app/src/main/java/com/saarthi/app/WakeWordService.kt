package com.saarthi.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject

class WakeWordService : Service(), RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val CHANNEL_ID = "saarthi_channel"

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        loadModel()
    }

    private fun startForegroundServiceWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Saarthi Listening", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saarthi")
            .setContentText("Model load ho raha hai...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun loadModel() {
        StorageService.unpack(this, "model", "model",
            { loadedModel ->
                model = loadedModel
                startListening()
            },
            { exception ->
                updateNotification("Model load error: ${exception.message}")
            }
        )
    }

    private fun startListening() {
        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            updateNotification("Sun raha hoon...")
        } catch (e: Exception) {
            updateNotification("Error: ${e.message}")
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
            val text = (json.optString("text", "") + json.optString("partial", "")).lowercase()
            if (text.contains("saarthi") || text.contains("sarthi") || text.contains("सारथी") || text.contains("सारथि")) {
                updateNotification("Ji, bataiye!")
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
