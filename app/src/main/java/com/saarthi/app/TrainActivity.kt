package com.saarthi.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread

class TrainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var isRecording = false
    private var recorder: AudioRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)

        val title = TextView(this)
        title.text = "Apni Awaaz Train Karo"
        title.textSize = 24f

        val instructions = TextView(this)
        instructions.text = "Button dabाके rakho, 'Saarthi' bolo, chhod do. Jitni baar chaho utni baar karo (kam se kam 8-10 baar behtar hoga)."
        instructions.textSize = 14f
        instructions.setPadding(0, 20, 0, 40)

        statusText = TextView(this)
        statusText.textSize = 18f
        statusText.setPadding(0, 0, 0, 40)
        updateStatus()

        val recordButton = Button(this)
        recordButton.text = "Dabाके Rakho aur Bolo"
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    recordButton.text = "Bol rahe hain..."
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndSave()
                    recordButton.text = "Dabाके Rakho aur Bolo"
                }
            }
            true
        }

        val resetButton = Button(this)
        resetButton.text = "Sab Hataओ (Reset)"
        resetButton.setOnClickListener {
            TemplateStore.clearAll(this)
            updateStatus()
            Toast.makeText(this, "Sab samples hata diye", Toast.LENGTH_SHORT).show()
        }

        val doneButton = Button(this)
        doneButton.text = "Wapas Jao"
        doneButton.setOnClickListener { finish() }

        layout.addView(title)
        layout.addView(instructions)
        layout.addView(statusText)
        layout.addView(recordButton)
        layout.addView(resetButton)
        layout.addView(doneButton)
        setContentView(layout)
    }

    private fun updateStatus() {
        val saved = TemplateStore.countSaved(this)
        statusText.text = "$saved samples save hue hain"
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission chahiye", Toast.LENGTH_SHORT).show()
            return
        }
        isRecording = true
        thread {
            val sampleRate = VoicePrint.SAMPLE_RATE
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
            )
            val collected = mutableListOf<Short>()
            val chunk = ShortArray(800)
            recorder?.startRecording()
            while (isRecording) {
                val n = recorder?.read(chunk, 0, chunk.size) ?: 0
                if (n > 0) {
                    for (i in 0 until n) collected.add(chunk[i])
                }
            }
            recorder?.stop()
            recorder?.release()

            if (collected.size > 1600) {
                val audioArray = collected.toShortArray()
                val features = VoicePrint.extractFeatures(audioArray)
                TemplateStore.save(this, features)
                runOnUiThread {
                    updateStatus()
                    Toast.makeText(this, "Sample saved!", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Bahut chhota tha, dobara try karein", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopRecordingAndSave() {
        isRecording = false
    }
}
