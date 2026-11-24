package com.neonrush.game

import com.neonrush.game.game.GameEngine
import com.neonrush.game.game.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun step(e: GameEngine, seconds: Float, dt: Float = 0.016f) {
        var t = 0f
        while (t < seconds) {
            e.update(dt)
            t += dt
        }
    }

    @Test
    fun speedRampsUpWithTimeAndCaps() {
        // Coin Rush never dies, so the ramp can be observed without steering.
        val e = GameEngine(GameMode.COIN_RUSH, 1L)
        step(e, 10f)
        assertEquals(GameEngine.BASE_SPEED + GameEngine.RAMP_PER_SEC * 10f, e.speed, 1.0f)

        val capped = GameEngine(GameMode.COIN_RUSH, 1L)
        step(capped, 120f)
        assertEquals(GameEngine.MAX_SPEED, capped.speed, 1.0f)
    }

    @Test
    fun sameSeedProducesIdenticalWorld() {
        val a = GameEngine(GameMode.CLASSIC, 42L)
        val b = GameEngine(GameMode.CLASSIC, 42L)
        step(a, 6f)
        step(b, 6f)
        assertEquals(a.obstacles.size, b.obstacles.size)
        assertEquals(a.coinList.size, b.coinList.size)
        assertEquals(a.distance, b.distance, 0.01f)
        if (a.obstacles.isNotEmpty()) {
            assertEquals(a.obstacles[0].z, b.obstacles[0].z, 0.01f)
            assertEquals(a.obstacles[0].x, b.obstacles[0].x, 0.01f)
        }
    }

    @Test
    fun differentSeedsProduceDifferentWorlds() {
        val a = GameEngine(GameMode.CLASSIC, 42L)
        val b = GameEngine(GameMode.CLASSIC, 43L)
        step(a, 6f)
        step(b, 6f)
        val sameWorld = a.obstacles.size == b.obstacles.size &&
            a.obstacles.zip(b.obstacles).all { (x, y) -> x.z == y.z && x.x == y.x }
        assertFalse(sameWorld)
    }

    @Test
    fun laneSwitchMovesPlayerAcrossLanes() {
        val e = GameEngine(GameMode.CLASSIC, 5L)
        assertEquals(0f, e.playerX, 0.01f) // starts center
        e.moveLane(1)
        step(e, 1f)
        assertEquals(GameEngine.LANE_W, e.playerX, 0.05f)
        e.moveLane(-1)
        e.moveLane(-1)
        step(e, 1f)
        assertEquals(-GameEngine.LANE_W, e.playerX, 0.05f)
    }

    @Test
    fun laneSwitchClampsAtEdges() {
        val e = GameEngine(GameMode.CLASSIC, 5L)
        e.moveLane(-1)
        e.moveLane(-1)
        e.moveLane(-1)
        step(e, 1f)
        assertEquals(-GameEngine.LANE_W, e.playerX, 0.05f)
        e.moveLane(1)
        e.moveLane(1)
        e.moveLane(1)
        e.moveLane(1)
        step(e, 1f)
        assertEquals(GameEngine.LANE_W, e.playerX, 0.05f)
    }

    @Test
    fun jumpArcGoesUpAndComesBackDown() {
        val e = GameEngine(GameMode.CLASSIC, 5L)
        e.jump()
        assertFalse(e.onGround)
        var peak = 0f
        var frames = 0
        while (!e.onGround && frames < 200) {
            e.update(0.016f)
            peak = maxOf(peak, e.playerY)
            frames++
        }
        assertTrue("peak height must clear a low barrier", peak > GameEngine.LOW_H)
        assertTrue(peak < GameEngine.TALL_H - 0.5f) // tall barriers are never clearable
        assertTrue(e.onGround)
    }

    @Test
    fun jumpOnlyWhenGrounded() {
        val e = GameEngine(GameMode.CLASSIC, 5L)
        e.jump()
        e.update(0.016f) // vy is now below the launch value
        val vyMid = e.vy
        assertTrue(vyMid < GameEngine.JUMP_V)
        e.jump() // airborne — must be ignored
        assertEquals(vyMid, e.vy, 0.001f)
    }

    @Test
    fun collisionWithTallBarrierKillsInClassicMode() {
        val e = GameEngine(GameMode.CLASSIC, 7L)
        e.placeRunner(1) // center lane
        e.forceObstacle(1, 0f, tall = true) // right on top of the player
        e.update(0.016f)
        assertFalse(e.alive)
        assertTrue(e.finished)
    }

    @Test
    fun adjacentLaneBarrierIsSafe() {
        val e = GameEngine(GameMode.CLASSIC, 7L)
        e.placeRunner(1)
        e.forceObstacle(0, 0f, tall = true) // left lane, player center
        e.update(0.016f)
        assertTrue(e.alive)
    }

    @Test
    fun jumpingClearsLowBarrierButNotTallOne() {
        // Low barrier, airborne above it -> survives.
        val a = GameEngine(GameMode.CLASSIC, 7L)
        a.placeRunner(1, y = GameEngine.LOW_H + 0.3f)
        a.forceObstacle(1, 0f, tall = false)
        a.update(0.016f)
        assertTrue("low barrier must be clearable by a jump", a.alive)

        // Tall barrier, same height -> still hits.
        val b = GameEngine(GameMode.CLASSIC, 7L)
        b.placeRunner(1, y = GameEngine.LOW_H + 0.3f)
        b.forceObstacle(1, 0f, tall = true)
        b.update(0.016f)
        assertFalse(b.alive)
    }

    @Test
    fun coinPickupCounts() {
        val e = GameEngine(GameMode.CLASSIC, 7L)
        e.placeRunner(1)
        e.forceCoin(0f, 1.0f, 0.5f) // center lane, near height, right on the player
        e.update(0.016f)
        assertEquals(1, e.coins)
        assertEquals(GameEngine.COIN_VALUE_CLASSIC, e.score())
    }

    @Test
    fun coinInOtherLaneIsNotCollected() {
        val e = GameEngine(GameMode.CLASSIC, 7L)
        e.placeRunner(1)
        e.forceCoin(-GameEngine.LANE_W, 1.0f, 0.5f)
        e.update(0.016f)
        assertEquals(0, e.coins)
    }

    @Test
    fun coinRushEndsAtSixtySecondsAndSurvivesHits() {
        val e = GameEngine(GameMode.COIN_RUSH, 9L)
        e.setCursor(60f) // keep spawns out of the way for this scenario
        e.placeRunner(1)
        e.forceObstacle(1, 0f, tall = true)
        e.update(0.016f)
        assertTrue("hit must stun, not kill", e.alive)
        assertTrue(e.hits >= 1)
        assertTrue(e.stun > 0f)
        step(e, 70f)
        assertTrue(e.finished)
        assertEquals(GameEngine.COIN_VALUE_RUSH * e.coins, e.score())
    }

    @Test
    fun classicScoreCombinesDistanceAndCoins() {
        val e = GameEngine(GameMode.CLASSIC, 3L)
        e.placeRunner(1)
        e.forceCoin(0f, 1.0f, 0.5f)
        e.update(0.016f) // collects the coin
        step(e, 2f)
        val expected = (e.distance / 5f).toInt() + e.coins * GameEngine.COIN_VALUE_CLASSIC
        assertEquals(expected, e.score())
    }

    @Test
    fun quitEndsRunKeepsScore() {
        val e = GameEngine(GameMode.CLASSIC, 11L)
        e.placeRunner(1)
        step(e, 3f)
        val before = e.score()
        assertTrue(before > 0)
        e.quit()
        assertTrue(e.finished)
        assertTrue(e.alive)
        assertEquals(before, e.score())
    }

    @Test
    fun worldNeverSpawnsUnreachablePattern() {
        // With any seed, every pair of consecutive tall barriers must leave
        // at least one free lane at the same z (checked against spawned worlds).
        for (seed in 1L..12L) {
            val e = GameEngine(GameMode.CLASSIC, seed)
            step(e, 14f)
            val tall = e.obstacles.filter { it.tall }.sortedBy { it.z }
            for (i in 0 until tall.size - 1) {
                val a = tall[i]
                val b = tall[i + 1]
                val sameBand = kotlin.math.abs(a.z - b.z) < GameEngine.OB_D
                if (sameBand) {
                    val freeLanes = GameEngine.LANES.count { lx ->
                        kotlin.math.abs(lx - a.x) > 1.5f && kotlin.math.abs(lx - b.x) > 1.5f
                    }
                    assertTrue("seed $seed produced a fully blocked band", freeLanes >= 1)
                }
            }
        }
    }

    @Test
    fun dailyModeUsesSameSeedMechanism() {
        val keyA = "2026-11-24"
        val a1 = GameEngine(GameMode.DAILY, com.neonrush.game.game.DailyChallenge.seedFor(keyA))
        val a2 = GameEngine(GameMode.DAILY, com.neonrush.game.game.DailyChallenge.seedFor(keyA))
        step(a1, 5f)
        step(a2, 5f)
        assertEquals(a1.obstacles.size, a2.obstacles.size)
        val b = GameEngine(GameMode.DAILY, com.neonrush.game.game.DailyChallenge.seedFor("2026-11-25"))
        step(b, 5f)
        val sameWorld = a1.obstacles.size == b.obstacles.size &&
            a1.obstacles.zip(b.obstacles).all { (x, y) -> x.z == y.z && x.x == y.x }
        assertFalse("different days must play differently", sameWorld)
    }
}
