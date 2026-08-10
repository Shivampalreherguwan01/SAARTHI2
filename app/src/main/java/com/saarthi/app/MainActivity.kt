package com.saarthi.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var toggleSwitch: Switch
    private lateinit var statusText: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            checkOverlayAndStart()
        } else {
            Toast.makeText(this, "Microphone permission zaroori hai", Toast.LENGTH_LONG).show()
            toggleSwitch.isChecked = false
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startService()
    }

    private var suppressListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)
        layout.gravity = android.view.Gravity.CENTER_HORIZONTAL

        val title = TextView(this)
        title.text = "Saarthi"
        title.textSize = 30f
        title.setPadding(0, 0, 0, 40)

        statusText = TextView(this)
        statusText.textSize = 16f
        statusText.setPadding(0, 0, 0, 20)

        toggleSwitch = Switch(this)
        toggleSwitch.textOn = "ON"
        toggleSwitch.textOff = "OFF"
        toggleSwitch.showText = true
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListener) return@setOnCheckedChangeListener
            if (isChecked) {
                requestPermissionsAndStart()
            } else {
                stopService(Intent(this, WakeWordService::class.java))
                refreshStatus()
            }
        }

        val trainButton = android.widget.Button(this)
        trainButton.text = "Awaaz Train Karo"
        trainButton.setOnClickListener {
            startActivity(Intent(this, TrainActivity::class.java))
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(toggleSwitch)
        layout.addView(trainButton)
        setContentView(layout)

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        suppressListener = true
        toggleSwitch.isChecked = WakeWordService.isRunning
        suppressListener = false
        statusText.text = if (WakeWordService.isRunning) "Saarthi active hai" else "Saarthi band hai"
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
        Handler(Looper.getMainLooper()).postDelayed({ refreshStatus() }, 500)
    }
}
