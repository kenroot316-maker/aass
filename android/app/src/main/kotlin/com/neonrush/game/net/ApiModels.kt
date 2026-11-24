package com.neonrush.game.net

/** DTOs shared with the Vercel + Neon backend. Gson serializes by field names. */

data class HealthResponse(
    val ok: Boolean = false,
    val status: String? = null,
    val database: String? = null,
    val version: String? = null
)


data class PlayerDto(
    val id: String,
    val nickname: String,
    val createdAt: String? = null
)

data class RegisterRequest(
    val deviceId: String,
    val nickname: String? = null
)

data class RegisterResponse(
    val player: PlayerDto
)

data class RenameRequest(val nickname: String)

data class ScoreSubmitRequest(
    val playerId: String,
    val mode: String,
    val score: Int,
    val coins: Int,
    val seconds: Int,
    val dailyKey: String? = null
)

data class ScoreSubmitResponse(
    val accepted: Boolean,
    val score: Int,
    val rank: Long? = null,
    val bestScore: Int,
    val newAchievements: List<String> = emptyList(),
    val totalPlayers: Long = 0
)

data class LeaderboardEntry(
    val rank: Long,
    val playerId: String,
    val nickname: String,
    val score: Int,
    val coins: Int = 0
)

data class LeaderboardResponse(
    val mode: String,
    val key: String? = null,
    val entries: List<LeaderboardEntry> = emptyList()
)

data class PlayerStatsResponse(
    val player: PlayerDto,
    val bestClassic: Int = 0,
    val bestCoinrush: Int = 0,
    val bestDaily: Int = 0,
    val gamesPlayed: Int = 0,
    val achievements: List<String> = emptyList(),
    val rankClassic: Long? = null,
    val rankCoinrush: Long? = null,
    val rankDaily: Long? = null
)
