package com.saarthi.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

object CommandExecutor {

    fun execute(context: Context, target: String): Boolean {
        val lower = target.lowercase()

        if (lower.contains("camera")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }

        val appMatch = findInstalledApp(context, lower)
        if (appMatch != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(appMatch)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return true
            }
        }

        return false
    }

    fun goHome(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun findInstalledApp(context: Context, searchTerm: String): String? {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(searchTerm) || searchTerm.contains(label)) {
                return app.packageName
            }
        }
        return null
    }
}
