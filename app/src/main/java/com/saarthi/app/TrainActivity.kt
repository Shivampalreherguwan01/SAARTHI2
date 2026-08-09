package com.saarthi.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread

class TrainActivity : AppCompatActivity() {

    private var currentSample = 1
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)

        val title = TextView(this)
        title.text = "Saarthi Train Karo"
        title.textSize = 24f

        statusText = TextView(this)
        statusText.textSize = 16f
        statusText.setPadding(0, 30, 0, 60)
        updateStatus()

        recordButton = Button(this)
        recordButton.text = "Record Sample $currentSample"
        recordButton.setOnClickListener {
            recordSample()
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(recordButton)
        setContentView(layout)
    }

    private fun updateStatus() {
        val saved = TemplateStore.countSaved(this)
        statusText.text = "$saved / 5 samples saved"
    }

    private fun recordSample() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission chahiye", Toast.LENGTH_SHORT).show()
            return
        }

        recordButton.isEnabled = false
        recordButton.text = "Bol rahe hain... (2 second)"

        thread {
            val sampleRate = VoicePrint.SAMPLE_RATE
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
            )

            val totalSamples = sampleRate * 2
            val audioData = ShortArray(totalSamples)
            recorder.startRecording()
            var read = 0
            while (read < totalSamples) {
                val n = recorder.read(audioData, read, totalSamples - read)
                if (n > 0) read += n
            }
            recorder.stop()
            recorder.release()

            val features = VoicePrint.extractFeatures(audioData)
            TemplateStore.save(this, currentSample, features)

            runOnUiThread {
                Toast.makeText(this, "Sample $currentSample saved!", Toast.LENGTH_SHORT).show()
                updateStatus()
                currentSample++
                if (currentSample > 5) {
                    statusText.text = "Sab 5 samples ho gaye! Ab wapas jaake activate karo."
                    recordButton.isEnabled = false
                    recordButton.text = "Training Complete"
                } else {
                    recordButton.isEnabled = true
                    recordButton.text = "Record Sample $currentSample"
                }
            }
        }
    }
}
