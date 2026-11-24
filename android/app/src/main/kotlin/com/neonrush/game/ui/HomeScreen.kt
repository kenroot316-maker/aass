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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonrush.game.game.DailyChallenge
import com.neonrush.game.game.GameMode
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonCyan
import com.neonrush.game.ui.theme.NeonLime
import com.neonrush.game.ui.theme.NeonMagenta
import com.neonrush.game.ui.theme.NeonSurface

@Composable
fun HomeScreen(
    store: LocalStore,
    onPlay: (GameMode) -> Unit,
    onLeaderboard: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0E1A), Color(0xFF120C26), Color(0xFF0B0E1A))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(44.dp))
        Text(
            text = "NEON RUSH 3D",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.verticalGradient(listOf(NeonCyan, Color(0xFF7A4BC9)))
            ),
            letterSpacing = 3.sp
        )
        Text(
            text = "Outrun the light.",
            fontSize = 15.sp,
            color = NeonMagenta,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(34.dp))

        ModeButton(
            title = GameMode.CLASSIC.label,
            subtitle = "Endless. Speed rises. One hit ends it.",
            color = NeonCyan,
            best = store.localBestClassic,
            onClick = { onPlay(GameMode.CLASSIC) }
        )
        Spacer(Modifier.height(13.dp))
        ModeButton(
            title = GameMode.COIN_RUSH.label,
            subtitle = "60 seconds. Grab every coin you can.",
            color = NeonLime,
            best = store.localBestRush,
            onClick = { onPlay(GameMode.COIN_RUSH) }
        )
        Spacer(Modifier.height(13.dp))
        ModeButton(
            title = GameMode.DAILY.label,
            subtitle = "Today: ${DailyChallenge.todayKey()} - same run for everyone.",
            color = NeonMagenta,
            best = store.localBestDaily,
            onClick = { onPlay(GameMode.DAILY) }
        )

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallNavButton("Leaderboard", Modifier.weight(1f), onLeaderboard)
            SmallNavButton("Profile", Modifier.weight(1f), onProfile)
            SmallNavButton("Settings", Modifier.weight(1f), onSettings)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Playing as ${store.nickname}",
            fontSize = 13.sp,
            color = Color(0xFF8892A6)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "3 lanes - swipe to steer - tap to jump",
            fontSize = 11.sp,
            color = Color(0xFF5A6480)
        )
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    color: Color,
    best: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonSurface,
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.size(10.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.weight(1f))
                Text("Best $best", fontSize = 13.sp, color = Color(0xFF8892A6))
            }
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color(0xFFB9C2D8),
                modifier = Modifier.padding(top = 6.dp, start = 20.dp)
            )
        }
    }
}

@Composable
private fun SmallNavButton(title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NeonSurface)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NeonCyan)
        }
    }
}
