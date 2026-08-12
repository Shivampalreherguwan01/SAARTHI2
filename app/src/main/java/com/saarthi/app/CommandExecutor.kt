package com.saarthi.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

object CommandExecutor {

    fun getInstalledAppLabels(context: Context): List<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val labels = mutableListOf<String>()
        for (info in resolveInfos) {
            labels.add(info.loadLabel(pm).toString())
        }
        return labels.distinct()
    }

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

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.equals(target, ignoreCase = true) || label.lowercase() == lower) {
                val packageName = info.activityInfo.packageName
                val launch = pm.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return true
                }
            }
        }

        val fuzzyMatch = findClosestApp(context, target)
        if (fuzzyMatch != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(fuzzyMatch)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
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

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }

    private fun findClosestApp(context: Context, target: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val lowerTarget = target.lowercase()

        var bestPackage: String? = null
        var bestDistance = Int.MAX_VALUE

        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString().lowercase()
            val dist = editDistance(label, lowerTarget)
            val threshold = (label.length * 0.4).toInt().coerceAtLeast(2)
            if (dist <= threshold && dist < bestDistance) {
                bestDistance = dist
                bestPackage = info.activityInfo.packageName
            }
        }
        return bestPackage
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
