package com.neonrush.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.game.Achievements
import com.neonrush.game.game.GameMode
import com.neonrush.game.game.GameResult
import com.neonrush.game.net.Repo
import com.neonrush.game.net.ScoreSubmitRequest
import com.neonrush.game.net.ScoreSubmitResponse
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonCyan
import com.neonrush.game.ui.theme.NeonLime
import com.neonrush.game.ui.theme.NeonMagenta
import com.neonrush.game.ui.theme.NeonSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private sealed interface SubmitState {
    object Working : SubmitState
    data class Done(val response: ScoreSubmitResponse) : SubmitState
    object Offline : SubmitState
}

@Composable
fun ResultsScreen(
    store: LocalStore,
    scope: CoroutineScope,
    result: GameResult?,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit
) {
    if (result == null) {
        onHome()
        return
    }

    var state by remember { mutableStateOf<SubmitState>(SubmitState.Working) }

    LaunchedEffect(result) {
        // Local bookkeeping happens no matter what.
        store.gamesPlayed += 1
        if (result.score > store.bestForMode(result.mode)) {
            store.setBestForMode(result.mode, result.score)
        }

        scope.launch {
            val playerId = Repo.ensurePlayer(store)
            if (playerId == null) {
                state = SubmitState.Offline
                return@launch
            }
            val response = Repo.submitScore(
                store,
                ScoreSubmitRequest(
                    playerId = playerId,
                    mode = result.mode.id,
                    score = result.score,
                    coins = result.coins,
                    seconds = result.seconds.toInt(),
                    dailyKey = result.dailyKey
                )
            )
            if (response == null || !response.accepted) {
                state = SubmitState.Offline
            } else {
                store.achievements = store.achievements + response.newAchievements
                state = SubmitState.Done(response)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(result.mode.label, fontSize = 16.sp, color = Color(0xFF8892A6))
        Spacer(Modifier.height(8.dp))
        Text("${result.score}", fontSize = 72.sp, fontWeight = FontWeight.Black, color = NeonCyan)
        Text("score", fontSize = 14.sp, color = Color(0xFF8892A6))

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${result.coins}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonLime)
                Text("coins", fontSize = 12.sp, color = Color(0xFF8892A6))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.1f".format(result.seconds), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("seconds", fontSize = 12.sp, color = Color(0xFF8892A6))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (result.score >= store.bestForMode(result.mode)) "NEW BEST" else "Best ${store.bestForMode(result.mode)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.score >= store.bestForMode(result.mode) && result.score > 0) NeonMagenta else Color(0xFF8892A6)
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        when (val s = state) {
            SubmitState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Submitting score...", fontSize = 14.sp, color = Color(0xFF8892A6))
            }
            SubmitState.Offline -> Text(
                "Offline - score kept on this device.",
                fontSize = 14.sp,
                color = Color(0xFF8892A6)
            )
            is SubmitState.Done -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val r = s.response.rank
                Text(
                    if (r != null) "World rank #$r" else "Score accepted",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime
                )
                if (s.response.totalPlayers > 0) {
                    Text(
                        "of ${s.response.totalPlayers} players",
                        fontSize = 12.sp,
                        color = Color(0xFF8892A6)
                    )
                }
                if (s.response.newAchievements.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    for (id in s.response.newAchievements) {
                        Text(
                            "Achievement unlocked: ${Achievements.titleOf(id)}",
                            fontSize = 13.sp,
                            color = NeonMagenta
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBg)
        ) {
            Text("PLAY AGAIN", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLeaderboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonSurface, contentColor = Color.White)
        ) {
            Text("Leaderboard", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonSurface, contentColor = Color.White)
        ) {
            Text("Home", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
    }
}
