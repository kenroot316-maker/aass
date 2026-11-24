package com.neonrush.game.game

/** Nickname rules shared by the UI. Pure Kotlin so tests cover it. */
object Nicknames {
    const val MIN = 3
    const val MAX = 16

    fun isValid(raw: String): Boolean {
        val t = raw.trim()
        if (t.length < MIN || t.length > MAX) return false
        return t.all { it.isLetterOrDigit() || it == '_' || it == ' ' }
    }
}
