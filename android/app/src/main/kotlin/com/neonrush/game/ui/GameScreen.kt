package com.neonrush.game.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.neonrush.game.game.Coin
import com.neonrush.game.game.DailyChallenge
import com.neonrush.game.game.GameEngine
import com.neonrush.game.game.GameEvent
import com.neonrush.game.game.GameMode
import com.neonrush.game.game.Obstacle
import com.neonrush.game.storage.LocalStore
import com.neonrush.game.ui.theme.NeonBg
import com.neonrush.game.ui.theme.NeonCyan
import com.neonrush.game.ui.theme.NeonLime
import com.neonrush.game.ui.theme.NeonMagenta
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Screen-space particles (visual layer only — the engine never touches these)
// ---------------------------------------------------------------------------
private class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val radius: Float
)

/** Same projection math as the renderer, reused to spawn particles at world events. */
private fun projectPoint(w: Float, h: Float, x: Float, y: Float, z: Float, camX: Float): Offset {
    val horizonY = h * 0.36f
    val focal = h * 0.95f
    val dz = (z + 6.5f).coerceAtLeast(0.7f)
    return Offset(w / 2f + (x - camX) * focal / dz, horizonY + (2.6f - y) * focal / dz)
}

private data class Star(val fx: Float, val fy: Float, val phase: Float, val size: Float)
private data class Building(val fx: Float, val fw: Float, val fh: Float, val lit: Boolean)

private val FogColor = Color(0xFF241748)
private val TrackNear = Color(0xFF1B1533)
private val TrackFar = Color(0xFF241C44)
private val DeepCyan = Color(0xFF0E7FA6)
private val DeepMagenta = Color(0xFF8F1153)

private fun fogAt(z: Float): Float = ((z - 34f) / 48f).coerceIn(0f, 1f)
private fun Color.fogged(z: Float, max: Float = 0.92f): Color = lerp(this, FogColor, fogAt(z) * max)

@Composable
fun GameScreen(
    mode: GameMode,
    store: LocalStore,
    onBack: () -> Unit,
    onFinished: (com.neonrush.game.game.GameResult) -> Unit
) {
    var runId by remember { mutableIntStateOf(0) }
    val seed = if (mode == GameMode.DAILY) {
        DailyChallenge.seedFor(DailyChallenge.todayKey())
    } else {
        System.nanoTime()
    }
    val engine = remember(runId) {
        if (runId == 0) GameEngine(mode, seed)
        else GameEngine(mode, if (mode == GameMode.DAILY) seed else seed + runId * 7919L)
    }
    var started by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var reported by remember(runId) { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var shake by remember { mutableStateOf(0f) }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { mutableStateListOf<Particle>() }
    val haptics = LocalHapticFeedback.current

    val stars = remember {
        val r = kotlin.random.Random(7)
        List(64) { Star(r.nextFloat(), r.nextFloat() * 0.32f, r.nextFloat() * 6.28f, 1f + r.nextFloat() * 1.8f) }
    }
    val buildings = remember {
        val r = kotlin.random.Random(21)
        List(26) { i ->
            Building(
                fx = r.nextFloat(), fw = 0.018f + r.nextFloat() * 0.05f,
                fh = 0.06f + r.nextFloat() * 0.20f, lit = i % 3 == 0
            )
        }
    }

    val tone = remember(store.soundOn) {
        if (store.soundOn) try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 72)
        } catch (t: Throwable) {
            null
        } else null
    }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    // ----------------------------- frame loop -----------------------------
    LaunchedEffect(started, runId) {
        if (!started) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive && !engine.finished) {
            withFrameNanos { now ->
                if (lastFrame == 0L) lastFrame = now
                val dt = ((now - lastFrame).toFloat() / 1_000_000_000f).coerceAtMost(0.05f)
                lastFrame = now
                if (!paused) engine.update(dt)

                val w = canvasPx.width.coerceAtLeast(1).toFloat()
                val h = canvasPx.height.coerceAtLeast(1).toFloat()
                val camX = engine.playerX * 0.72f
                if (!paused) {
                    for (ev in engine.events) {
                        val p = projectPoint(w, h, ev.x, ev.y, ev.z, camX)
                        when (ev.type) {
                            GameEvent.EventType.COIN -> {
                                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
                                burst(particles, p.x, p.y, 9, NeonLime)
                            }
                            GameEvent.EventType.HIT -> {
                                tone?.startTone(ToneGenerator.TONE_CDMA_PIP, 160)
                                if (store.hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                burst(particles, p.x, p.y, 16, NeonMagenta)
                                shake = 14f
                            }
                            GameEvent.EventType.DEATH -> {
                                tone?.startTone(ToneGenerator.TONE_SUP_ERROR, 260)
                                if (store.hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                burst(particles, p.x, p.y, 22, NeonMagenta)
                                shake = 20f
                            }
                            GameEvent.EventType.JUMP -> {
                                tone?.startTone(ToneGenerator.TONE_PROP_ACK, 40)
                            }
                            GameEvent.EventType.LAND -> {
                                burst(particles, p.x, p.y, 5, Color(0xFF8892A6))
                            }
                            GameEvent.EventType.LANE -> Unit
                        }
                    }
                }
                // particle physics
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 520f * dt
                    p.life -= dt
                    if (p.life <= 0f) it.remove()
                }
                shake *= 0.86f
            }
            tick++
        }
        if (!reported && engine.finished) {
            reported = true
            paused = false
            engine.paused = false
            onFinished(engine.result())
        }
    }

    // ----------------------------- input -----------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBg)
            .onSizeChanged { canvasPx = it }
            .pointerInput(started, runId) {
                if (started) {
                    detectTapGestures(onTap = { engine.jump() })
                }
            }
            .pointerInput(started, runId) {
                if (started) {
                    var accX = 0f
                    var accY = 0f
                    detectDragGestures(
                        onDragEnd = { accX = 0f; accY = 0f },
                        onDragCancel = { accX = 0f; accY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accX += amount.x
                            accY += amount.y
                            if (accX > 52f) {
                                engine.moveLane(1); accX = 0f; accY = 0f
                            } else if (accX < -52f) {
                                engine.moveLane(-1); accX = 0f; accY = 0f
                            } else if (accY < -64f) {
                                engine.jump(); accX = 0f; accY = 0f
                            }
                        }
                    )
                }
            }
    ) {
        tick // subscribe: HUD and Canvas recompose/redraw every frame

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(engine, tick, stars, buildings, particles, shake)
        }

        // ----------------------------- HUD -----------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp, start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("SCORE", fontSize = 10.sp, color = Color(0xFF8892A6), letterSpacing = 2.sp)
                Text(
                    "${engine.score()}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                Text(
                    "COINS ${engine.coins}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonLime,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                Text(
                    "II",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = if (paused) NeonCyan else Color(0xFF8892A6),
                    modifier = Modifier
                        .background(Color(0xFF131A2E).copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .clickable {
                            if (started && !engine.finished) {
                                paused = !paused
                                engine.paused = paused
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Coin Rush countdown bar
        if (mode == GameMode.COIN_RUSH && started) {
            val remaining = (GameEngine.COIN_RUSH_SECONDS - engine.time).coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 96.dp, start = 18.dp, end = 18.dp)
                    .height(5.dp)
                    .background(Color(0xFF131A2E), RoundedCornerShape(3.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(remaining / GameEngine.COIN_RUSH_SECONDS)
                        .fillMaxHeight()
                        .background(NeonMagenta, RoundedCornerShape(3.dp))
                )
            }
        }

        // Lane indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (i in 0..2) {
                val active = engine.targetLane == i
                Box(
                    Modifier
                        .width(if (active) 26.dp else 10.dp)
                        .height(10.dp)
                        .background(
                            if (active) NeonCyan else Color(0xFF3A4460),
                            RoundedCornerShape(6.dp)
                        )
                )
            }
        }

        // ----------------------------- overlays -----------------------------
        if (!started) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonBg.copy(alpha = 0.86f))
                    .pointerInput(runId) {
                        detectTapGestures { started = true }
                    },
                contentAlignment = Alignment.Center
            ) {
                val pulse = rememberInfiniteAlpha()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mode.label, fontSize = 32.sp, fontWeight = FontWeight.Black, color = NeonCyan)
                    Spacer(Modifier.height(18.dp))
                    ControlHint("SWIPE LEFT / RIGHT", "change lane")
                    ControlHint("SWIPE UP  or  TAP", "jump")
                    ControlHint("DODGE THE NEON WALLS", "collect the coins")
                    Spacer(Modifier.height(26.dp))
                    Text(
                        "TAP TO START",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonLime,
                        letterSpacing = 3.sp,
                        modifier = Modifier.alpha(pulse)
                    )
                    Spacer(Modifier.height(30.dp))
                    TextButton(onClick = onBack) { Text("Back", color = Color(0xFF8892A6)) }
                }
            }
        }

        if (started && paused && !engine.finished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonBg.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PAUSED", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(Modifier.height(24.dp))
                    OverlayButton("RESUME", NeonCyan) {
                        paused = false; engine.paused = false
                    }
                    Spacer(Modifier.height(10.dp))
                    OverlayButton("RESTART", Color.White) {
                        particles.clear()
                        shake = 0f
                        paused = false
                        started = false
                        runId++
                    }
                    Spacer(Modifier.height(10.dp))
                    OverlayButton("END RUN", NeonMagenta) {
                        paused = false; engine.paused = false; engine.quit()
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onBack) { Text("Back", color = Color(0xFF8892A6)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The 3D scene
// ---------------------------------------------------------------------------
private fun DrawScope.drawScene(
    engine: GameEngine,
    tick: Int,
    stars: List<Star>,
    buildings: List<Building>,
    particles: List<Particle>,
    shake: Float
) {
    val w = size.width
    val h = size.height
    val time = engine.time
    val horizonY = h * 0.36f
    val focal = h * 0.95f
    val camX = engine.playerX * 0.72f
    val camY = 2.6f

    fun proj(x: Float, y: Float, z: Float): Offset {
        val dz = (z + 6.5f).coerceAtLeast(0.7f)
        return Offset(w / 2f + (x - camX) * focal / dz, horizonY + (camY - y) * focal / dz)
    }

    fun sc(z: Float): Float = focal / (z + 6.5f).coerceAtLeast(0.7f)

    withTransform({
        if (shake > 0.4f) {
            translate(sin(tick * 41f) * shake, cos(tick * 37f) * shake * 0.6f)
        }
    }) {
        // ---- sky ----
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF0B0E1A), Color(0xFF181034), Color(0xFF2A1652)),
                startY = 0f, endY = horizonY
            ),
            topLeft = Offset(0f, 0f), size = Size(w, horizonY + 2f)
        )

        // ---- stars ----
        for (s in stars) {
            val sx = (((s.fx + camX * 0.02f) % 1f + 1f) % 1f) * w
            val twk = 0.45f + 0.55f * sin(time * 1.6f + s.phase)
            drawCircle(
                Color.White.copy(alpha = 0.75f * twk),
                radius = s.size,
                center = Offset(sx, s.fy * h)
            )
        }

        // ---- neon sun ----
        val sunC = Offset(w / 2f - camX * 10f, horizonY * 0.84f)
        val sunR = h * 0.085f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF2D95).copy(alpha = 0.30f), Color.Transparent),
                center = sunC, radius = sunR * 2.1f
            ),
            radius = sunR * 2.1f, center = sunC
        )
        drawCircle(Color(0xFFFF2D95), radius = sunR, center = sunC, style = Stroke(width = h * 0.006f))
        drawCircle(
            Color(0xFFFF6FBF).copy(alpha = 0.35f), radius = sunR * 0.66f, center = sunC,
            style = Stroke(width = h * 0.004f)
        )

        // ---- skyline ----
        for (b in buildings) {
            val bx = (((b.fx + camX * 0.04f) % 1.15f + 1.15f) % 1.15f - 0.075f) * w
            val bw = b.fw * w
            val bh = b.fh * horizonY
            drawRect(
                Color(0xFF120F26).copy(alpha = 0.9f),
                topLeft = Offset(bx, horizonY - bh), size = Size(bw, bh)
            )
            if (b.lit) {
                drawRect(
                    NeonCyan.copy(alpha = 0.30f),
                    topLeft = Offset(bx + bw * 0.28f, horizonY - bh * 0.55f),
                    size = Size(bw * 0.14f, bh * 0.10f)
                )
                drawRect(
                    NeonMagenta.copy(alpha = 0.25f),
                    topLeft = Offset(bx + bw * 0.55f, horizonY - bh * 0.35f),
                    size = Size(bw * 0.14f, bh * 0.08f)
                )
            }
        }

        // ---- ground + track ----
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF12101F), Color(0xFF0B0E1A)),
                startY = horizonY, endY = h
            ),
            topLeft = Offset(0f, horizonY), size = Size(w, h - horizonY)
        )
        val tw = GameEngine.TRACK_HALF_W
        val trackPath = Path().apply {
            val fl = proj(-tw, 0f, 95f)
            val fr = proj(tw, 0f, 95f)
            val nr = proj(tw, 0f, -5.6f)
            val nl = proj(-tw, 0f, -5.6f)
            moveTo(fl.x, fl.y); lineTo(fr.x, fr.y); lineTo(nr.x, nr.y); lineTo(nl.x, nl.y)
            close()
        }
        drawPath(
            trackPath,
            brush = Brush.verticalGradient(listOf(TrackFar, TrackNear), startY = horizonY, endY = h)
        )

        // ---- floor cross lines ----
        val gridPhase = engine.distance % 8f
        for (k in 0..13) {
            val z = k * 8f - gridPhase
            if (z < -5.4f) continue
            val a = (1f - fogAt(z)) * 0.20f
            if (a <= 0.01f) continue
            drawLine(
                Color(0xFF6F7BA8).copy(alpha = a),
                proj(-tw, 0f, z), proj(tw, 0f, z), strokeWidth = 2f
            )
        }

        // ---- lane divider dashes ----
        val dashPhase = engine.distance % 4.5f
        for (lx in floatArrayOf(-GameEngine.LANE_W / 2f, GameEngine.LANE_W / 2f)) {
            for (k in 0..22) {
                val z0 = k * 4.5f - dashPhase
                val z1 = z0 + 1.9f
                if (z1 < -5.4f) continue
                val zc = (z0 + z1) / 2f
                val a = (1f - fogAt(zc)) * 0.55f
                if (a <= 0.02f) continue
                drawLine(
                    NeonCyan.copy(alpha = a * 0.7f),
                    proj(lx, 0.02f, z0.coerceAtLeast(-5.6f)), proj(lx, 0.02f, z1),
                    strokeWidth = 3f
                )
            }
        }

        // ---- side rails (glowing) ----
        for (side in floatArrayOf(-tw, tw)) {
            var prev = proj(side, 0.28f, -5.4f)
            for (k in 1..12) {
                val z = -5.4f + k * 5.2f
                val cur = proj(side, 0.28f, z)
                val a = 1f - fogAt(z)
                val rail = lerp(Color(0xFF7A4BC9), NeonCyan, a)
                drawLine(rail.copy(alpha = 0.16f * a + 0.05f), prev, cur, strokeWidth = 12f)
                drawLine(rail.copy(alpha = 0.85f * a + 0.05f), prev, cur, strokeWidth = 3.5f)
                prev = cur
            }
        }

        // ---- entities, far to near ----
        val drawList = buildList {
            engine.obstacles.forEach { add(Pair(it.z, it)) }
            engine.coinList.forEach { add(Pair(it.z, it as Any)) }
        }.sortedByDescending { it.first }

        for ((_, obj) in drawList) {
            if (obj is Obstacle) drawObstacle(obj, ::proj, ::sc, camX)
            else drawCoin(obj as Coin, time, ::proj, ::sc)
        }

        // ---- player ----
        drawPlayer(engine, time, ::proj, ::sc)

        // ---- particles ----
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(p.color.copy(alpha = a), radius = p.radius * (0.5f + a * 0.8f), center = Offset(p.x, p.y))
        }

        // ---- speed lines ----
        val ratio = ((engine.speed - 22f) / (GameEngine.MAX_SPEED - 22f)).coerceIn(0f, 1f)
        if (ratio > 0f) {
            val alpha = ratio * 0.30f
            val cx = w / 2f
            val cy = horizonY
            for (i in 0 until 12) {
                val ang = (i / 12f) * 6.2832f + time * 0.6f
                val r0 = h * 0.30f
                val r1 = h * (0.52f + 0.10f * sin(time * 3f + i))
                drawLine(
                    Color.White.copy(alpha = alpha),
                    Offset(cx + cos(ang) * r0 * 1.35f, cy + sin(ang) * r0),
                    Offset(cx + cos(ang) * r1 * 1.35f, cy + sin(ang) * r1),
                    strokeWidth = 2f
                )
            }
        }

        // ---- stun flash ----
        if (engine.stun > 0f) {
            val a = (engine.stun / GameEngine.STUN_SECONDS) * 0.45f
            drawRect(Color(0xFFFF2D55).copy(alpha = a), topLeft = Offset(0f, 0f), size = Size(w, h))
        }

        // ---- vignette ----
        drawRect(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                center = Offset(w / 2f, h * 0.52f), radius = h * 0.78f
            ),
            topLeft = Offset(0f, 0f), size = Size(w, h)
        )
    }
}

private fun DrawScope.drawObstacle(
    o: Obstacle,
    proj: (Float, Float, Float) -> Offset,
    sc: (Float) -> Float,
    camX: Float
) {
    val hw = o.halfW
    val hd = o.halfD
    val zF = o.z - hd
    val zB = o.z + hd
    if (zB < -5.6f) return

    val base = if (o.tall) NeonMagenta else Color(0xFFFF8A3C) // tall = magenta wall, low = orange hurdle
    val sideC = lerp(base, Color(0xFF0E0B1E), 0.45f).fogged(o.z)
    val topC = lerp(base, Color.White, 0.35f).fogged(o.z)
    val frontTop = base.fogged(o.z)
    val frontBottom = DeepMagenta.fogged(o.z)

    // side face (the one visible given camera position)
    val showLeft = o.x > camX
    val sidePath = Path().apply {
        val sx = if (showLeft) o.x - hw else o.x + hw
        val b1 = proj(sx, 0f, zF)
        val b2 = proj(sx, 0f, zB)
        val t2 = proj(sx, o.h, zB)
        val t1 = proj(sx, o.h, zF)
        moveTo(b1.x, b1.y); lineTo(b2.x, b2.y); lineTo(t2.x, t2.y); lineTo(t1.x, t1.y)
        close()
    }
    drawPath(sidePath, sideC)

    // top face
    val topPath = Path().apply {
        val t1 = proj(o.x - hw, o.h, zF)
        val t2 = proj(o.x + hw, o.h, zF)
        val t3 = proj(o.x + hw, o.h, zB)
        val t4 = proj(o.x - hw, o.h, zB)
        moveTo(t1.x, t1.y); lineTo(t2.x, t2.y); lineTo(t3.x, t3.y); lineTo(t4.x, t4.y)
        close()
    }
    drawPath(topPath, topC)

    // front face with vertical gradient + neon edges
    val flb = proj(o.x - hw, 0f, zF)
    val frb = proj(o.x + hw, 0f, zF)
    val frt = proj(o.x + hw, o.h, zF)
    val flt = proj(o.x - hw, o.h, zF)
    val frontPath = Path().apply {
        moveTo(flb.x, flb.y); lineTo(frb.x, frb.y); lineTo(frt.x, frt.y); lineTo(flt.x, flt.y); close()
    }
    drawPath(
        frontPath,
        brush = Brush.verticalGradient(listOf(frontTop, frontBottom), startY = flt.y, endY = flb.y)
    )
    // horizontal light band
    val bandY = flb.y + (frt.y - flb.y) * 0.62f
    drawLine(
        Color.White.copy(alpha = 0.55f * (1f - fogAt(o.z)) + 0.05f),
        Offset(flb.x, bandY), Offset(frb.x, bandY),
        strokeWidth = (sc(o.z) * 0.055f).coerceAtLeast(1.5f)
    )
    drawPath(frontPath, Color.White.copy(alpha = 0.75f), style = Stroke(width = 2f))
    drawPath(frontPath, frontTop.copy(alpha = 0.20f), style = Stroke(width = 10f))
}

private fun DrawScope.drawCoin(
    c: Coin,
    time: Float,
    proj: (Float, Float, Float) -> Offset,
    sc: (Float) -> Float
) {
    if (c.taken || c.z < -5f) return
    val bob = 0.08f * sin(time * 4f + c.z * 0.5f)
    val p = proj(c.x, c.y + bob, c.z)
    val r = 0.42f * sc(c.z) * (1f + 0.05f * sin(time * 6f + c.z))
    if (r < 1.2f) return
    val wob = abs(cos(time * 5f + c.z * 0.4f)) * 0.85f + 0.15f
    val body = NeonLime.fogged(c.z)

    drawCircle(
        brush = Brush.radialGradient(
            listOf(NeonLime.copy(alpha = 0.40f * (1f - fogAt(c.z))), Color.Transparent),
            center = p, radius = r * 2.4f
        ),
        radius = r * 2.4f, center = p
    )
    drawOval(
        color = body,
        topLeft = Offset(p.x - r * wob, p.y - r),
        size = Size(2f * r * wob, 2f * r)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(p.x - r * wob, p.y - r),
        size = Size(2f * r * wob, 2f * r),
        style = Stroke(width = (r * 0.14f).coerceAtLeast(1.2f))
    )
    drawCircle(
        Color.White.copy(alpha = 0.9f),
        radius = r * 0.22f * wob,
        center = Offset(p.x - r * 0.22f, p.y - r * 0.30f)
    )
}

private fun DrawScope.drawPlayer(
    engine: GameEngine,
    time: Float,
    proj: (Float, Float, Float) -> Offset,
    sc: (Float) -> Float
) {
    val s0 = sc(0f)
    val px = engine.playerX
    val py = engine.playerY

    // shadow on the floor
    val shadowC = proj(px, 0f, 0f)
    val sw = s0 * 0.50f * (1f - (py / 5f).coerceIn(0f, 0.7f))
    drawOval(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(shadowC.x - sw, shadowC.y - sw * 0.34f),
        size = Size(2f * sw, 2f * sw * 0.34f)
    )

    val baseP = proj(px, py, 0f)
    val headP = proj(px, py + GameEngine.RUNNER_HEIGHT, 0f)
    val bodyW = s0 * 0.90f
    val bodyH = baseP.y - headP.y

    // aura
    val midY = (baseP.y + headP.y) / 2f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(NeonCyan.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(baseP.x, midY), radius = s0 * 1.5f
        ),
        radius = s0 * 1.5f, center = Offset(baseP.x, midY)
    )

    // thruster flame
    val flick = 0.8f + 0.4f * sin(time * 22f)
    drawCircle(
        Color.White.copy(alpha = 0.75f),
        radius = s0 * 0.10f * flick, center = Offset(baseP.x, baseP.y + s0 * 0.10f)
    )
    drawCircle(
        NeonCyan.copy(alpha = 0.30f),
        radius = s0 * 0.22f * flick, center = Offset(baseP.x, baseP.y + s0 * 0.14f)
    )

    // body with banking tilt while changing lanes
    val tilt = ((GameEngine.LANES[engine.targetLane] - px) * 14f).coerceIn(-16f, 16f)
    val stunned = engine.stun > 0f
    val bodyColor = if (stunned && sin(time * 28f) > 0f) Color(0xFFFFC24D) else NeonCyan
    withTransform({ rotate(tilt, pivot = baseP) }) {
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(bodyColor, DeepCyan), startY = headP.y, endY = baseP.y),
            topLeft = Offset(baseP.x - bodyW / 2f, headP.y),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.48f, bodyW * 0.48f)
        )
        // visor
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(baseP.x - bodyW * 0.30f, headP.y + bodyH * 0.14f),
            size = Size(bodyW * 0.60f, bodyH * 0.16f),
            cornerRadius = CornerRadius(bodyW * 0.16f, bodyW * 0.16f)
        )
        // accent band
        drawRoundRect(
            color = NeonMagenta.copy(alpha = 0.85f),
            topLeft = Offset(baseP.x - bodyW * 0.34f, headP.y + bodyH * 0.52f),
            size = Size(bodyW * 0.68f, bodyH * 0.07f),
            cornerRadius = CornerRadius(bodyW * 0.08f, bodyW * 0.08f)
        )
    }
}

private fun burst(list: MutableList<Particle>, x: Float, y: Float, n: Int, color: Color) {
    val r = kotlin.random.Random.Default
    for (i in 0 until n) {
        val ang = r.nextFloat() * 6.2832f
        val spd = 90f + r.nextFloat() * 240f
        list.add(
            Particle(
                x = x, y = y,
                vx = cos(ang) * spd, vy = sin(ang) * spd - 120f,
                life = 0.45f + r.nextFloat() * 0.3f, maxLife = 0.75f,
                color = if (i % 4 == 0) Color.White else color,
                radius = 3f + r.nextFloat() * 5f
            )
        )
    }
}

@Composable
private fun rememberInfiniteAlpha(): Float {
    val trans = rememberInfiniteTransition(label = "pulse")
    val v = trans.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(650),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return v.value
}

@Composable
private fun ControlHint(main: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(main, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(sub, fontSize = 12.sp, color = Color(0xFF8892A6))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverlayButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(220.dp).height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF131A2E), contentColor = color)
    ) {
        Text(label, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}
