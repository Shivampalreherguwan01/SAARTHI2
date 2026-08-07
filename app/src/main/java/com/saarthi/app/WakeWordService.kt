package com.saarthi.app

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val CHANNEL_ID = "saarthi_channel"

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        startListening()
    }

    private fun startForegroundServiceWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Saarthi Listening", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saarthi")
            .setContentText("Sun raha hoon...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun startListening() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.get(0)?.lowercase() ?: ""
                if (text.contains("saarthi") || text.contains("सारथी")) {
                    // Wake word detected!
                    val notification = NotificationCompat.Builder(this@WakeWordService, CHANNEL_ID)
                        .setContentTitle("Saarthi")
                        .setContentText("Ji, bataiye!")
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .build()
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(1, notification)
                }
                restartListening()
            }

            override fun onError(error: Int) {
                restartListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun restartListening() {
        speechRecognizer?.destroy()
        startListening()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
