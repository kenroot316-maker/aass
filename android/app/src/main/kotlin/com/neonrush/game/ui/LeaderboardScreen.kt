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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.neonrush.game.game.DailyChallenge
import com.neonrush.game.game.GameMode
import com.neonrush.game.net.LeaderboardEntry
import com.neonrush.game.net.Repo
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonCyan
import com.neonrush.game.ui.theme.NeonDim
import com.neonrush.game.ui.theme.NeonLime
import com.neonrush.game.ui.theme.NeonMagenta
import com.neonrush.game.ui.theme.NeonSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private sealed interface LbState {
    object Loading : LbState
    data class Ready(val entries: List<LeaderboardEntry>) : LbState
    object Error : LbState
}

@Composable
fun LeaderboardScreen(
    store: LocalStore,
    scope: CoroutineScope,
    initialMode: String,
    onBack: () -> Unit
) {
    var tab by remember {
        mutableStateOf(
            GameMode.entries.firstOrNull { it.id == initialMode } ?: GameMode.CLASSIC
        )
    }
    var state by remember { mutableStateOf<LbState>(LbState.Loading) }

    LaunchedEffect(tab) {
        state = LbState.Loading
        val key = if (tab == GameMode.DAILY) DailyChallenge.todayKey() else null
        val res = Repo.leaderboard(store, tab.id, key)
        state = if (res == null) LbState.Error else LbState.Ready(res.entries)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonSurface, contentColor = NeonCyan)
            ) {
                Text("< Back")
            }
            Spacer(Modifier.width(14.dp))
            Text("Leaderboard", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GameMode.entries.forEach { m ->
                val selected = m == tab
                Button(
                    onClick = { tab = m },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) NeonCyan else NeonSurface,
                        contentColor = if (selected) NeonBg else Color.White
                    )
                ) {
                    Text(
                        when (m) {
                            GameMode.CLASSIC -> "Classic"
                            GameMode.COIN_RUSH -> "Rush"
                            GameMode.DAILY -> "Daily"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        if (tab == GameMode.DAILY) {
            Text(
                "Today: ${DailyChallenge.todayKey()}  (resets 00:00 UTC)",
                fontSize = 12.sp,
                color = Color(0xFF8892A6),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            LbState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
            LbState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not reach the leaderboard.\nCheck your internet and try again.", fontSize = 14.sp, color = Color(0xFF8892A6))
            }
            is LbState.Ready -> {
                if (s.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No scores yet.\nBe the first to set a record!",
                            fontSize = 14.sp,
                            color = Color(0xFF8892A6)
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.entries, key = { it.rank.toString() + it.playerId }) { e ->
                            val mine = e.playerId == store.playerId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(
                                        if (mine) NeonCyan.copy(alpha = 0.14f) else NeonSurface,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "#${e.rank}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when {
                                        e.rank <= 1L -> NeonLime
                                        e.rank <= 3L -> NeonMagenta
                                        else -> Color(0xFF8892A6)
                                    },
                                    modifier = Modifier.width(52.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.nickname + if (mine) " (you)" else "",
                                        fontSize = 15.sp,
                                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                                        color = if (mine) NeonCyan else Color.White,
                                        maxLines = 1
                                    )
                                }
                                Text("${e.score}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
