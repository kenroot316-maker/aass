package com.neonrush.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neonrush.game.game.GameMode
import com.neonrush.game.game.GameResult
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.HomeScreen
import com.neonrush.game.ui.LeaderboardScreen
import com.neonrush.game.ui.ProfileScreen
import com.neonrush.game.ui.ResultsScreen
import com.neonrush.game.ui.SettingsScreen
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonRushTheme
import com.neonrush.game.ui.GameScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Top-level navigation destinations. */
enum class Screen { HOME, GAME, RESULTS, LEADERBOARD, PROFILE, SETTINGS }

class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(this)

        setContent {
            NeonRushTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NeonBg) {
                    var screen by remember { mutableStateOf(Screen.HOME) }
                    var currentMode by remember { mutableStateOf(GameMode.CLASSIC) }
                    var lastResult by remember { mutableStateOf<GameResult?>(null) }
                    var refreshLeaderboardAtMode by remember { mutableStateOf("") }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NeonBg)
                    ) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                store = store,
                                onPlay = { mode -> currentMode = mode; screen = Screen.GAME },
                                onLeaderboard = { screen = Screen.LEADERBOARD },
                                onProfile = { screen = Screen.PROFILE },
                                onSettings = { screen = Screen.SETTINGS }
                            )
                            Screen.GAME -> GameScreen(
                                mode = currentMode,
                                store = store,
                                onBack = { screen = Screen.HOME },
                                onFinished = { result ->
                                    lastResult = result
                                    screen = Screen.RESULTS
                                }
                            )
                            Screen.RESULTS -> ResultsScreen(
                                store = store,
                                scope = appScope,
                                result = lastResult,
                                onRetry = { screen = Screen.GAME },
                                onHome = { screen = Screen.HOME },
                                onLeaderboard = {
                                    refreshLeaderboardAtMode = lastResult?.mode?.id ?: ""
                                    screen = Screen.LEADERBOARD
                                }
                            )
                            Screen.LEADERBOARD -> LeaderboardScreen(
                                store = store,
                                scope = appScope,
                                initialMode = refreshLeaderboardAtMode,
                                onBack = { screen = Screen.HOME }
                            )
                            Screen.PROFILE -> ProfileScreen(
                                store = store,
                                scope = appScope,
                                onBack = { screen = Screen.HOME }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                store = store,
                                scope = appScope,
                                onBack = { screen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appScope.cancel()
        com.neonrush.game.MusicManager.release()
    }
}
