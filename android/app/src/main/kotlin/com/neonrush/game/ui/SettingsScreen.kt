package com.neonrush.game.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.neonrush.game.BuildConfig
import com.neonrush.game.net.ApiConfig
import com.neonrush.game.net.Repo
import com.neonrush.game.net.normalizeBaseUrl
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonCyan
import com.neonrush.game.ui.theme.NeonLime
import com.neonrush.game.ui.theme.NeonMagenta
import com.neonrush.game.ui.theme.NeonSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    store: LocalStore,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    var sound by remember { mutableStateOf(store.soundOn) }
    var haptics by remember { mutableStateOf(store.hapticsOn) }
    var confirmReset by remember { mutableStateOf(false) }

    val effectiveUrl = store.serverUrl.ifBlank { ApiConfig.DEFAULT_BASE_URL }
    var urlDraft by remember { mutableStateOf(effectiveUrl) }
    var serverStatus by remember { mutableStateOf("") }

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
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(Modifier.height(18.dp))

        SettingToggle(
            title = "Sound effects",
            subtitle = "Beeps on coins, jumps and hits",
            checked = sound,
            onChange = { sound = it; store.soundOn = it }
        )
        Spacer(Modifier.height(10.dp))
        SettingToggle(
            title = "Vibration",
            subtitle = "Haptic feedback on impact",
            checked = haptics,
            onChange = { haptics = it; store.hapticsOn = it }
        )

        Spacer(Modifier.height(18.dp))

        Text("GAME SERVER", fontSize = 12.sp, color = Color(0xFF8892A6))
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it; serverStatus = "" },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                label = { Text("Backend base url", fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color(0xFF3A4460),
                    cursorColor = NeonCyan
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val normalized = normalizeBaseUrl(urlDraft)
                        if (normalized == null) {
                            serverStatus = "Invalid URL"
                        } else {
                            store.serverUrl = normalized
                            urlDraft = normalized
                            serverStatus = "Saved. Testing..."
                            scope.launch {
                                val res = Repo.health(store)
                                serverStatus = when {
                                    res == null -> "Unreachable (offline play still works)"
                                    res.ok && res.database == "connected" -> "Server OK - database connected"
                                    res.ok -> "Server OK - database not connected yet"
                                    else -> "Server reachable - status: ${res.status ?: "?"}"
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonBg)
                ) {
                    Text("Save + Test", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        store.serverUrl = ""
                        urlDraft = ApiConfig.DEFAULT_BASE_URL
                        serverStatus = "Reset to default server"
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonSurface, contentColor = Color(0xFF8892A6))
                ) {
                    Text("Default")
                }
            }
            if (serverStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    serverStatus,
                    fontSize = 12.sp,
                    color = when {
                        serverStatus.startsWith("Server OK") || serverStatus.startsWith("Saved") -> NeonLime
                        serverStatus == "Invalid URL" -> NeonMagenta
                        else -> Color(0xFF8892A6)
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scores, leaderboard and achievements sync through this server. If it is unreachable the game stays fully playable offline.",
                fontSize = 11.sp,
                color = Color(0xFF8892A6)
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { confirmReset = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonSurface, contentColor = NeonMagenta)
        ) {
            Text("Reset local data")
        }

        Spacer(Modifier.weight(1f))
        Text("ABOUT", fontSize = 12.sp, color = Color(0xFF8892A6))
        Text(
            "Neon Rush ${BuildConfig.VERSION_NAME} (3D)\n" +
                "Package: ${BuildConfig.APPLICATION_ID}\n" +
                "Server: ${store.serverUrl.ifBlank { ApiConfig.DEFAULT_BASE_URL }}\n" +
                "Scores and achievements sync when online.",
            fontSize = 12.sp,
            color = Color(0xFF8892A6),
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset local data?") },
            text = { Text("This clears your nickname, local best scores and offline achievements on this device. Online records stay on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    store.resetAll()
                    sound = true
                    haptics = true
                    urlDraft = ApiConfig.DEFAULT_BASE_URL
                }) { Text("Reset", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Color(0xFF8892A6)) }
            }
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF8892A6))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NeonCyan,
                checkedThumbColor = NeonBg
            )
        )
    }
}
