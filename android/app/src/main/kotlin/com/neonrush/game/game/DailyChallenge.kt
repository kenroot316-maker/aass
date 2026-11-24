package com.neonrush.game.game

import java.util.Calendar
import java.util.TimeZone

/**
 * Daily Challenge key + seed. Everyone in the world who plays "today" (UTC)
 * gets exactly the same obstacle sequence, and the daily leaderboard resets
 * at midnight UTC. Uses only java.util so it works on minSdk 24 without
 * desugaring.
 */
object DailyChallenge {

    /** UTC calendar date key, e.g. "2026-11-24". */
    fun todayKey(nowUtcMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = nowUtcMillis
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    /** Key for "tomorrow" relative to a given moment (used to show reset countdowns). */
    fun shiftKey(key: String, days: Int): String {
        val parts = key.split("-").map { it.toInt() }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        cal.add(Calendar.DAY_OF_MONTH, days)
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    /**
     * Deterministic 64-bit seed derived from the date key. String.hashCode() is
     * stable across JVMs, but we use a stronger explicit mix for good measure.
     */
    fun seedFor(key: String): Long {
        var h = 1125899906842597L
        for (c in key) h = h * 31L + c.code
        // Final avalanche mix (splitmix64-style) using unsigned math because the
        // multiplier does not fit in a signed Long literal.
        var u = h.toULong()
        u = u xor (u shr 29)
        u *= 0xBF58476D1CE4E5B9UL
        u = u xor (u shr 32)
        return u.toLong()
    }
}
