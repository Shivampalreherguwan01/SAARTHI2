package com.saarthi.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            Toast.makeText(this, "Microphone permission zaroori hai", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)

        val textView = TextView(this)
        textView.text = "Saarthi Ready"
        textView.textSize = 28f

        val button = Button(this)
        button.text = "Saarthi Activate Karo"
        button.setOnClickListener {
            requestPermissionsAndStart()
        }

        layout.addView(textView)
        layout.addView(button)
        setContentView(layout)
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
