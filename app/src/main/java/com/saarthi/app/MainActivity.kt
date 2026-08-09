package com.saarthi.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            checkOverlayAndStart()
        } else {
            Toast.makeText(this, "Microphone permission zaroori hai", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)

        val textView = TextView(this)
        textView.text = "Saarthi Ready"
        textView.textSize = 28f

        toggleButton = Button(this)
        toggleButton.setOnClickListener {
            if (WakeWordService.isRunning) {
                stopService(Intent(this, WakeWordService::class.java))
                refreshButtonState()
            } else {
                requestPermissionsAndStart()
            }
        }

        val trainButton = Button(this)
        trainButton.text = "Saarthi Train Karo"
        trainButton.setOnClickListener {
            startActivity(Intent(this, TrainActivity::class.java))
        }

        layout.addView(textView)
        layout.addView(toggleButton)
        layout.addView(trainButton)
        setContentView(layout)

        refreshButtonState()
    }

    override fun onResume() {
        super.onResume()
        refreshButtonState()
    }

    private fun refreshButtonState() {
        if (WakeWordService.isRunning) {
            toggleButton.text = "Saarthi: ON (band karne ke liye dabaओ)"
        } else {
            toggleButton.text = "Saarthi: OFF (chalू karne ke liye dabaओ)"
        }
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        } else {
            startService()
        }
    }

    private fun startService() {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Handler(Looper.getMainLooper()).postDelayed({ refreshButtonState() }, 500)
    }
}
