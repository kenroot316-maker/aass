package com.neonrush.game

import com.google.gson.Gson
import com.neonrush.game.net.LeaderboardResponse
import com.neonrush.game.net.PlayerStatsResponse
import com.neonrush.game.net.ScoreSubmitResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiModelsTest {

    private val gson = Gson()

    @Test
    fun parsesLeaderboardResponse() {
        val json = """
            {"mode":"classic","key":null,
             "entries":[
               {"rank":1,"playerId":"p1","nickname":"Ace","score":3100,"coins":12},
               {"rank":2,"playerId":"p2","nickname":"Byte","score":2800,"coins":8}
             ]}
        """.trimIndent()
        val r = gson.fromJson(json, LeaderboardResponse::class.java)
        assertEquals("classic", r.mode)
        assertNull(r.key)
        assertEquals(2, r.entries.size)
        assertEquals("Ace", r.entries[0].nickname)
        assertEquals(3100, r.entries[0].score)
        assertEquals(1L, r.entries[0].rank)
    }

    @Test
    fun parsesSubmitResponseWithRank() {
        val json = """
            {"accepted":true,"score":1250,"rank":14,"bestScore":1250,
             "newAchievements":["first_run","classic_500"],"totalPlayers":231}
        """.trimIndent()
        val r = gson.fromJson(json, ScoreSubmitResponse::class.java)
        assertEquals(true, r.accepted)
        assertEquals(1250, r.score)
        assertEquals(14L, r.rank)
        assertEquals(231L, r.totalPlayers)
        assertEquals(listOf("first_run", "classic_500"), r.newAchievements)
    }

    @Test
    fun parsesSubmitResponseWithoutRank() {
        val json = """{"accepted":true,"score":10,"bestScore":0,"newAchievements":[],"totalPlayers":0}"""
        val r = gson.fromJson(json, ScoreSubmitResponse::class.java)
        assertNull(r.rank)
        assertEquals(0, r.newAchievements.size)
    }

    @Test
    fun parsesPlayerStats() {
        val json = """
            {"player":{"id":"p9","nickname":"Nova","createdAt":"2026-11-24T00:00:00Z"},
             "bestClassic":900,"bestCoinrush":400,"bestDaily":620,"gamesPlayed":7,
             "achievements":["first_run","rush_20"],
             "rankClassic":42,"rankCoinrush":null,"rankDaily":3}
        """.trimIndent()
        val r = gson.fromJson(json, PlayerStatsResponse::class.java)
        assertEquals("Nova", r.player.nickname)
        assertEquals(900, r.bestClassic)
        assertEquals(7, r.gamesPlayed)
        assertEquals(2, r.achievements.size)
        assertEquals(42L, r.rankClassic)
        assertNull(r.rankCoinrush)
        assertEquals(3L, r.rankDaily)
    }
}
