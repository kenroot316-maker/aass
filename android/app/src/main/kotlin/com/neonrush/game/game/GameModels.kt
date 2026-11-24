package com.neonrush.game.game

/** The three playable modes of Neon Rush. */
enum class GameMode(val id: String, val label: String) {
    CLASSIC("classic", "Classic Run"),
    COIN_RUSH("coinrush", "Coin Rush"),
    DAILY("daily", "Daily Challenge");
}

/**
 * A neon barrier on the 3-lane track. Pure data; the engine mutates z.
 *
 * World space: x = lateral (lanes at -2.4, 0, +2.4), y = height (0 = floor),
 * z = depth ahead of the player (player sits at z = 0, spawn at z = 110).
 */
data class Obstacle(
    var z: Float,
    val x: Float,
    val h: Float,          // barrier height; low barriers can be jumped
    val tall: Boolean      // true = cannot be jumped
) {
    val halfW: Float = GameEngine.OB_W / 2f
    val halfD: Float = GameEngine.OB_D / 2f
}

/** A collectible coin hovering above the track. */
data class Coin(
    var z: Float,
    val x: Float,
    val y: Float,
    var taken: Boolean = false
)

/** One-shot gameplay event the UI turns into sound, haptics and particles. */
data class GameEvent(
    val type: EventType,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    enum class EventType { COIN, HIT, JUMP, LAND, LANE, DEATH }
}

/** Immutable summary of a finished run, safe to carry across screens. */
data class GameResult(
    val mode: GameMode,
    val score: Int,
    val coins: Int,
    val seconds: Float,
    val distance: Float,
    val dailyKey: String? = null,
    val died: Boolean = false
)
