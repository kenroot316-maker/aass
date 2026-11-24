package com.neonrush.game.game

/** Achievement definitions shown in the app. Unlock logic is authoritative on
 *  the server; the app only renders ids returned by the API. */
data class AchievementDef(val id: String, val title: String, val description: String)

object Achievements {
    val ALL: List<AchievementDef> = listOf(
        AchievementDef("first_run", "First Light", "Complete your first run"),
        AchievementDef("classic_500", "Warming Up", "Score 500+ in one Classic Run"),
        AchievementDef("classic_1500", "Blazing", "Score 1,500+ in one Classic Run"),
        AchievementDef("classic_3000", "Neon Legend", "Score 3,000+ in one Classic Run"),
        AchievementDef("rush_20", "Coin Snatcher", "Collect 20+ coins in one Coin Rush"),
        AchievementDef("rush_45", "Coin Storm", "Collect 45+ coins in one Coin Rush"),
        AchievementDef("daily_player", "Daily Spark", "Play a Daily Challenge"),
        AchievementDef("regular", "Regular", "Play 5 total runs")
    )

    private val byId: Map<String, AchievementDef> = ALL.associateBy { it.id }

    fun titleOf(id: String): String = byId[id]?.title ?: id
    fun descriptionOf(id: String): String = byId[id]?.description ?: ""
}
