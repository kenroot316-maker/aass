package com.neonrush.game

import com.neonrush.game.game.DailyChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeTest {

    @Test
    fun keyMatchesDateFormat() {
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(DailyChallenge.todayKey()))
    }

    @Test
    fun sameMomentGivesSameKeyAndSeed() {
        val now = 1_700_000_000_000L
        val k1 = DailyChallenge.todayKey(now)
        val k2 = DailyChallenge.todayKey(now)
        assertEquals(k1, k2)
        assertEquals(DailyChallenge.seedFor(k1), DailyChallenge.seedFor(k2))
    }

    @Test
    fun differentDaysGiveDifferentSeeds() {
        val a = DailyChallenge.todayKey(1_700_000_000_000L)
        val b = DailyChallenge.todayKey(1_700_086_400_000L)
        assertNotEquals(a, b)
        assertNotEquals(DailyChallenge.seedFor(a), DailyChallenge.seedFor(b))
    }

    @Test
    fun seedIsStableAcrossCalls() {
        val k = "2026-11-24"
        assertEquals(DailyChallenge.seedFor(k), DailyChallenge.seedFor(k))
    }

    @Test
    fun shiftKeyCrossesMonthBoundaries() {
        assertEquals("2026-11-25", DailyChallenge.shiftKey("2026-11-24", 1))
        assertEquals("2026-12-01", DailyChallenge.shiftKey("2026-11-30", 1))
        assertEquals("2027-01-01", DailyChallenge.shiftKey("2026-12-31", 1))
        assertEquals("2026-11-23", DailyChallenge.shiftKey("2026-11-24", -1))
    }
}
