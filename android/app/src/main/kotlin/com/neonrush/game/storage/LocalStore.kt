package com.neonrush.game.storage

import android.content.Context
import android.provider.Settings

/**
 * SharedPreferences-backed local store. Only simple fields — anything with
 * logic lives in pure Kotlin classes so it stays unit-testable.
 */
class LocalStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("neon_rush", Context.MODE_PRIVATE)

    /** Stable per-device identifier used for the auto account. */
    val deviceId: String by lazy {
        val saved = prefs.getString(KEY_DEVICE, null)
        if (saved != null) saved else {
            val generated = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString()
            // ANDROID_ID can reset on factory reset; persist whatever we resolved.
            prefs.edit().putString(KEY_DEVICE, generated).apply()
            generated
        }
    }

    var playerId: String?
        get() = prefs.getString(KEY_PLAYER, null)
        set(value) = prefs.edit().putString(KEY_PLAYER, value).apply()

    var nickname: String
        get() = prefs.getString(KEY_NICK, null) ?: "Runner"
        set(value) = prefs.edit().putString(KEY_NICK, value).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /**
     * Game server base url override. Empty string = use ApiConfig.DEFAULT_BASE_URL.
     * Editable in Settings so the APK can be pointed at any deployment.
     */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var localBestClassic: Int
        get() = prefs.getInt(KEY_BEST_CLASSIC, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_CLASSIC, value).apply()

    var localBestRush: Int
        get() = prefs.getInt(KEY_BEST_RUSH, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_RUSH, value).apply()

    var localBestDaily: Int
        get() = prefs.getInt(KEY_BEST_DAILY, 0)
        set(value) = prefs.edit().putInt(KEY_BEST_DAILY, value).apply()

    var gamesPlayed: Int
        get() = prefs.getInt(KEY_GAMES, 0)
        set(value) = prefs.edit().putInt(KEY_GAMES, value).apply()

    /** Unlocked achievement ids, comma-joined (server is authoritative). */
    var achievements: Set<String>
        get() = (prefs.getString(KEY_ACH, "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
        set(value) = prefs.edit().putString(KEY_ACH, value.joinToString(",")).apply()

    fun bestForMode(mode: com.neonrush.game.game.GameMode): Int = when (mode) {
        com.neonrush.game.game.GameMode.CLASSIC -> localBestClassic
        com.neonrush.game.game.GameMode.COIN_RUSH -> localBestRush
        com.neonrush.game.game.GameMode.DAILY -> localBestDaily
    }

    fun setBestForMode(mode: com.neonrush.game.game.GameMode, value: Int) {
        when (mode) {
            com.neonrush.game.game.GameMode.CLASSIC -> localBestClassic = value
            com.neonrush.game.game.GameMode.COIN_RUSH -> localBestRush = value
            com.neonrush.game.game.GameMode.DAILY -> localBestDaily = value
        }
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_DEVICE = "device_id"
        const val KEY_PLAYER = "player_id"
        const val KEY_NICK = "nickname"
        const val KEY_SOUND = "sound_on"
        const val KEY_HAPTICS = "haptics_on"
        const val KEY_SERVER = "server_url"
        const val KEY_BEST_CLASSIC = "best_classic"
        const val KEY_BEST_RUSH = "best_coinrush"
        const val KEY_BEST_DAILY = "best_daily"
        const val KEY_GAMES = "games_played"
        const val KEY_ACH = "achievements"
    }
}
