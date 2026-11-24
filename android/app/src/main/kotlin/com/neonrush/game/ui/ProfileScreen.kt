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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.neonrush.game.game.Nicknames
import com.neonrush.game.net.PlayerStatsResponse
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

@Composable
fun ProfileScreen(
    store: LocalStore,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    var stats by remember { mutableStateOf<PlayerStatsResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showRename by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val id = Repo.ensurePlayer(store)
        stats = if (id == null) null else Repo.stats(store, id)
        loading = false
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
            Text("Profile", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(Modifier.height(18.dp))

        // Nickname card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NICKNAME", fontSize = 11.sp, color = Color(0xFF8892A6))
                Text(store.nickname, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
            }
            Button(
                onClick = { showRename = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBg)
            ) {
                Text("Edit")
            }
        }

        Spacer(Modifier.height(14.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
        } else if (stats == null) {
            Text(
                "Offline - showing local data only.\nConnect to the internet to sync your profile.",
                fontSize = 13.sp,
                color = Color(0xFF8892A6)
            )
            StatRows(null)
        } else {
            StatRows(stats)
        }

        Spacer(Modifier.height(14.dp))
        Text("ACHIEVEMENTS", fontSize = 12.sp, color = Color(0xFF8892A6))
        Spacer(Modifier.height(8.dp))
        val unlocked: Set<String> = stats?.achievements?.toSet() ?: store.achievements
        Achievements.ALL.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                for (a in rowItems) {
                    val got = a.id in unlocked
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (got) NeonCyan.copy(alpha = 0.12f) else NeonDim.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            a.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (got) NeonLime else Color(0xFF6B7690)
                        )
                        Text(
                            a.description,
                            fontSize = 11.sp,
                            color = if (got) Color.White else Color(0xFF6B7690)
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(store.nickname) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Change nickname") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (Nicknames.isValid(name)) "OK" else "3-16 letters, digits, _ or space",
                            color = if (Nicknames.isValid(name)) NeonLime else NeonMagenta
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = name.trim()
                        if (Nicknames.isValid(clean)) {
                            showRename = false
                            store.nickname = clean
                            val pid = store.playerId
                            if (pid != null) scope.launch { Repo.rename(store, pid, clean) }
                        }
                    },
                    enabled = Nicknames.isValid(name)
                ) { Text("Save", color = NeonCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel", color = Color(0xFF8892A6)) }
            }
        )
    }
}

@Composable
private fun StatRows(stats: PlayerStatsResponse?) {
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow("Best Classic Run", stats?.bestClassic ?: 0, stats?.rankClassic)
        StatRow("Best Coin Rush", stats?.bestCoinrush ?: 0, stats?.rankCoinrush)
        StatRow("Best Daily", stats?.bestDaily ?: 0, stats?.rankDaily)
        StatRow("Games played", stats?.gamesPlayed ?: 0, null)
    }
}

@Composable
private fun StatRow(label: String, value: Int, rank: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFFB9C2D8), modifier = Modifier.weight(1f))
        Text("$value", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (rank != null) {
            Spacer(Modifier.width(10.dp))
            Text("#$rank", fontSize = 12.sp, color = NeonCyan)
        }
    }
}
