package com.neonrush.game.game

import java.util.Random

/**
 * Neon Rush core engine — pure Kotlin, zero Android imports, fully deterministic
 * under a seed so unit tests and the Daily Challenge can rely on it.
 *
 * v2 "3D" world: a three-lane endless track. The player sits at z = 0 and can
 * change lanes (x = -2.4, 0, +2.4) and jump (y). Obstacles and coins spawn far
 * ahead (z = 110) and stream toward the player as distance accumulates.
 *
 * Everything gameplay-relevant happens here; the renderer only projects.
 */
class GameEngine(
    val mode: GameMode,
    seed: Long
) {
    companion object {
        const val LANE_W = 2.4f             // lateral lane spacing
        const val TRACK_HALF_W = 3.9f       // half width of the rideable floor
        const val RUNNER_HALF_W = 0.5f      // player body half width (world units)
        const val RUNNER_HEIGHT = 1.5f      // player body height

        const val SPAWN_Z = 110f            // entities appear this far ahead
        const val DESPAWN_Z = -12f          // removed behind the camera
        const val PLAYER_HIT_DEPTH = 0.8f   // half depth of the player hit box

        const val BASE_SPEED = 16f          // world units consumed per second
        const val RAMP_PER_SEC = 0.32f
        const val MAX_SPEED = 34f

        const val JUMP_V = 8.8f             // initial jump velocity (y units/s)
        const val GRAVITY = 24f             // downward accel (y units/s^2)

        const val COIN_RUSH_SECONDS = 60f
        const val COIN_VALUE_CLASSIC = 25
        const val COIN_VALUE_RUSH = 50
        const val STUN_SECONDS = 0.6f

        const val OB_W = 2.0f               // obstacle width (world units)
        const val OB_D = 1.6f               // obstacle depth
        const val LOW_H = 1.1f              // jumpable barrier height
        const val TALL_H = 2.8f             // full barrier height

        const val GAP_MIN = 15f             // distance between patterns (min)
        const val GAP_MAX = 24f             // distance between patterns (max)

        val LANES = floatArrayOf(-LANE_W, 0f, LANE_W)
    }

    private val rng = Random(seed)

    var time = 0f; private set
    var distance = 0f; private set
    var speed = BASE_SPEED; private set
    var coins = 0; private set
    var hits = 0; private set
    var alive = true; private set
    var finished = false; private set
    var paused = false
    var stun = 0f; private set

    // Lateral position (eased toward the target lane) and jump physics.
    var playerX = 0f; private set
    var playerY = 0f; private set
    var vy = 0f; private set
    var onGround = true; private set
    var targetLane = 1; private set          // 0 = left, 1 = center, 2 = right

    val obstacles = mutableListOf<Obstacle>()
    val coinList = mutableListOf<Coin>()
    val events = mutableListOf<GameEvent>()

    private var nextZ = 46f                  // spawn cursor (first pattern is friendly)

    // ---------- input ----------
    /** Ends the run early (Quit / End Run). Score so far is kept. */
    fun quit() {
        if (alive) finished = true
    }

    /** Moves one lane left (-1) or right (+1) with clamping. */
    fun moveLane(dir: Int) {
        if (!alive || finished || paused) return
        val newLane = (targetLane + dir).coerceIn(0, 2)
        if (newLane != targetLane) {
            targetLane = newLane
            events.add(GameEvent(GameEvent.EventType.LANE, LANES[targetLane], 0f, 0f))
        }
    }

    /** Jumps when grounded and not stunned. */
    fun jump() {
        if (!alive || finished || paused) return
        if (onGround && stun <= 0f) {
            vy = JUMP_V
            onGround = false
            events.add(GameEvent(GameEvent.EventType.JUMP, playerX, playerY, 0f))
        }
    }

    // ---------- main loop ----------
    fun update(dtRaw: Float) {
        if (!alive || finished || paused) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        time += dt
        events.clear()

        if (mode == GameMode.COIN_RUSH && time >= COIN_RUSH_SECONDS) {
            finished = true
            return
        }

        speed = (BASE_SPEED + RAMP_PER_SEC * time).coerceAtMost(MAX_SPEED)
        val effective = if (stun > 0f) speed * 0.55f else speed
        if (stun > 0f) stun = (stun - dt).coerceAtLeast(0f)
        distance += effective * dt

        // Lateral easing toward the target lane (frame-rate independent).
        val laneX = LANES[targetLane]
        val lerp = 1f - Math.pow(0.0004, dt.toDouble()).toFloat()
        playerX += (laneX - playerX) * lerp

        // Jump physics.
        if (!onGround) {
            vy -= GRAVITY * dt
            playerY += vy * dt
            if (playerY <= 0f) {
                playerY = 0f
                vy = 0f
                onGround = true
                events.add(GameEvent(GameEvent.EventType.LAND, playerX, 0f, 0f))
            }
        }

        val step = effective * dt
        for (o in obstacles) o.z -= step
        for (c in coinList) c.z -= step
        nextZ -= step

        spawnAhead()
        despawn()
        collide()
    }

    // ---------- spawning ----------
    private fun spawnAhead() {
        while (nextZ < SPAWN_Z) {
            val depth = spawnPattern(nextZ)
            nextZ += depth + GAP_MIN + rng.nextFloat() * (GAP_MAX - GAP_MIN)
        }
    }

    /** Spawns one pattern at z; returns its total depth so spawning chains correctly. */
    private fun spawnPattern(z: Float): Float {
        val roll = rng.nextFloat()
        val lane = rng.nextInt(3)
        return when {
            roll < 0.16f -> { // coin line in one lane
                val n = 4 + rng.nextInt(3)
                for (i in 0 until n) {
                    coinList.add(Coin(z + i * 3.2f, LANES[lane], 1.0f))
                }
                (n - 1) * 3.2f + 1f
            }
            roll < 0.34f -> { // single tall barrier + coin trail beside it
                obstacles.add(Obstacle(z, LANES[lane], TALL_H, true))
                if (rng.nextFloat() < 0.6f) {
                    val free = (lane + 1 + rng.nextInt(2)) % 3
                    for (i in 0 until 3) coinList.add(Coin(z + i * 2.6f, LANES[free], 1.0f))
                }
                OB_D
            }
            roll < 0.52f -> { // two tall barriers, one lane stays free
                val free = rng.nextInt(3)
                LANES.forEachIndexed { i, lx ->
                    if (i != free) obstacles.add(Obstacle(z, lx, TALL_H, true))
                }
                if (rng.nextFloat() < 0.5f) {
                    for (i in 0 until 3) coinList.add(Coin(z + i * 2.6f, LANES[free], 1.0f))
                }
                OB_D
            }
            roll < 0.68f -> { // full-width low hurdle — jump it; coin arc above
                obstacles.add(Obstacle(z, 0f, LOW_H, false))
                val arcLane = LANES[lane]
                coinList.add(Coin(z - 2.2f, arcLane, 1.2f))
                coinList.add(Coin(z, arcLane, 1.9f))
                coinList.add(Coin(z + 2.2f, arcLane, 1.2f))
                OB_D
            }
            roll < 0.82f -> { // floating coin arc only
                val n = 5
                for (i in 0 until n) {
                    val t = i / (n - 1).toFloat()
                    coinList.add(Coin(z + i * 2.4f, LANES[lane], 1.0f + 1.4f * kotlin.math.sin(t * Math.PI).toFloat()))
                }
                (n - 1) * 2.4f + 1f
            }
            roll < 0.92f -> { // staggered barriers across lanes
                val laneB = (lane + 1 + rng.nextInt(2)) % 3
                obstacles.add(Obstacle(z, LANES[lane], TALL_H, true))
                obstacles.add(Obstacle(z + 7f, LANES[laneB], TALL_H, true))
                7f + OB_D
            }
            else -> { // gate combo: tall gate, then low hurdle shortly after
                val free = rng.nextInt(3)
                LANES.forEachIndexed { i, lx ->
                    if (i != free) obstacles.add(Obstacle(z, lx, TALL_H, true))
                }
                obstacles.add(Obstacle(z + 9f, 0f, LOW_H, false))
                9f + OB_D
            }
        }
    }

    private fun despawn() {
        obstacles.removeAll { it.z + it.halfD < DESPAWN_Z }
        coinList.removeAll { it.z < DESPAWN_Z || it.taken }
    }

    // ---------- collisions ----------
    private fun collide() {
        val xPad = OB_W / 2f + RUNNER_HALF_W - 0.14f
        val hit = obstacles.firstOrNull { o ->
            val zOverlap = o.z - o.halfD < PLAYER_HIT_DEPTH && o.z + o.halfD > -PLAYER_HIT_DEPTH
            val xOverlap = kotlin.math.abs(playerX - o.x) < xPad
            val cleared = playerY > o.h - 0.15f
            zOverlap && xOverlap && !cleared
        }
        if (hit != null) {
            hits++
            when (mode) {
                GameMode.CLASSIC, GameMode.DAILY -> {
                    alive = false
                    finished = true
                    events.add(GameEvent(GameEvent.EventType.DEATH, playerX, playerY, hit.z))
                }
                GameMode.COIN_RUSH -> {
                    stun = STUN_SECONDS
                    events.add(GameEvent(GameEvent.EventType.HIT, hit.x, 1f, hit.z))
                    obstacles.remove(hit) // shatters; the runner passes through
                }
            }
        }

        for (c in coinList) {
            if (!c.taken) {
                val dz = c.z
                val dx = c.x - playerX
                val dy = (c.y - (playerY + RUNNER_HEIGHT * 0.45f))
                if (dz * dz + dx * dx < 1.3f && kotlin.math.abs(dy) < 1.1f) {
                    c.taken = true
                    coins++
                    events.add(GameEvent(GameEvent.EventType.COIN, c.x, c.y, c.z))
                }
            }
        }
    }

    // ---------- scoring ----------
    fun score(): Int = when (mode) {
        GameMode.CLASSIC, GameMode.DAILY -> (distance / 5f).toInt() + coins * COIN_VALUE_CLASSIC
        GameMode.COIN_RUSH -> coins * COIN_VALUE_RUSH
    }

    fun result(dailyKey: String? = null): GameResult = GameResult(
        mode = mode,
        score = score(),
        coins = coins,
        seconds = time,
        distance = distance,
        dailyKey = dailyKey ?: if (mode == GameMode.DAILY) DailyChallenge.todayKey() else null,
        died = !alive
    )

    // ---------- deterministic test hooks (not used by the UI) ----------
    internal fun forceObstacle(lane: Int, z: Float, tall: Boolean = true) {
        obstacles.add(Obstacle(z, LANES[lane.coerceIn(0, 2)], if (tall) TALL_H else LOW_H, tall))
    }

    internal fun forceCoin(x: Float, y: Float, z: Float) {
        coinList.add(Coin(z, x, y))
    }

    internal fun placeRunner(lane: Int, y: Float = 0f) {
        targetLane = lane.coerceIn(0, 2)
        playerX = LANES[targetLane]
        playerY = y
        onGround = y <= 0f
        vy = 0f
    }

    internal fun setCursor(z: Float) {
        nextZ = z
    }
}
