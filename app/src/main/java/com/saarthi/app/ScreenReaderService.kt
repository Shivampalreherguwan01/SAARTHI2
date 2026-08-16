package com.saarthi.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenReaderService : AccessibilityService() {

    companion object {
        @Volatile var instance: ScreenReaderService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun getCurrentAppName(): String {
        return try {
            rootInActiveWindow?.packageName?.toString() ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen padh nahi paya"
        val texts = mutableListOf<String>()
        extractText(root, texts, 0)
        return if (texts.isEmpty()) "Screen par koi text nahi mila" else texts.joinToString(" | ").take(3000)
    }

    private fun extractText(node: AccessibilityNodeInfo?, texts: MutableList<String>, depth: Int) {
        if (node == null || depth > 25) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank()) texts.add(text)
        if (!desc.isNullOrBlank() && desc != text) texts.add(desc)
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), texts, depth + 1)
        }
    }
}
