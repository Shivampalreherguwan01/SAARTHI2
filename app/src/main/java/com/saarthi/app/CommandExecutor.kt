package com.saarthi.app

import android.content.Context
import android.content.Intent
import android.provider.MediaStore

object CommandExecutor {

    fun tryExecute(context: Context, text: String): String? {
        val lower = text.lowercase()

        if (containsAny(lower, listOf("camera", "kaimara", "कैमरा", "फोटो", "photo"))) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return "Camera khol raha hoon"
            }
        }

        if (containsAny(lower, listOf("whatsapp", "व्हाट्सएप्प", "व्हाट्सएप"))) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "WhatsApp khol raha hoon"
            }
        }

        if (containsAny(lower, listOf("youtube", "यूट्यूब"))) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "YouTube khol raha hoon"
            }
        }

        if (containsAny(lower, listOf("chrome", "browser", "इंटरनेट", "क्रोम"))) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "Chrome khol raha hoon"
            }
        }

        if (containsAny(lower, listOf("gallery", "gallary", "गैलरी", "photos"))) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return "Gallery khol raha hoon"
            }
        }

        return null
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        for (k in keywords) {
            if (text.contains(k)) return true
        }
        return false
    }
}
