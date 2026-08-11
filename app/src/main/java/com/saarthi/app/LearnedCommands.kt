package com.saarthi.app

import android.content.Context

object LearnedCommands {
    private const val PREFS = "saarthi_learned"

    fun save(context: Context, phrase: String, action: String, target: String?, reply: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = phrase.lowercase().trim()
        prefs.edit().putString(key, "$action|${target ?: ""}|$reply").apply()
    }

    fun lookup(context: Context, phrase: String): GroqLLM.ActionResult? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = phrase.lowercase().trim()
        val stored = prefs.getString(key, null) ?: return null
        val parts = stored.split("|")
        if (parts.size < 3) return null
        val target = if (parts[1].isBlank()) null else parts[1]
        return GroqLLM.ActionResult(parts[0], target, parts[2])
    }
}
