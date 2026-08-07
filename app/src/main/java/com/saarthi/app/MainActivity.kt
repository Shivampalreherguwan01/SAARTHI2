package com.saarthi.app

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 200, 60, 60)

        val textView = TextView(this)
        textView.text = "Saarthi Ready"
        textView.textSize = 28f

        val button = Button(this)
        button.text = "Bolo Saarthi"
        button.setOnClickListener {
            tts.speak("Hello Shivam, main Saarthi hoon", TextToSpeech.QUEUE_FLUSH, null, null)
        }

        layout.addView(textView)
        layout.addView(button)
        setContentView(layout)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
