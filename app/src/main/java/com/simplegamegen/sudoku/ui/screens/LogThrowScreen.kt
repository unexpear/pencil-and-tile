package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlin.math.abs
import com.simplegamegen.sudoku.arcade.DrawOp
import com.simplegamegen.sudoku.arcade.LogThrow
import com.simplegamegen.sudoku.arcade.LogThrowArt
import com.simplegamegen.sudoku.arcade.Mode
import com.simplegamegen.sudoku.arcade.Phase
import com.simplegamegen.sudoku.arcade.ThrowState
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.say

private val LogThrowSetup: (PuzzleFactory) -> PlaySetup<ThrowState> = { factory ->
    PlaySetup(
        rules = "A round log spins in the centre. Tap to throw a dagger from the stack straight up into the rim. " +
            "It sticks and spins with the log. If it hits a dagger already there, it bounces off and the run ends. " +
            "Stick every dagger and the log bursts. Apples and coins on the rim score extra. Some logs start with daggers already stuck. " +
            "Endless stages speed up, reverse, and change pattern. The level map has 48 logs, a boss every fifth, and stars for the fruit you take.",
        settingTitle = "Game",
        settings = listOf("Log Throw"),
        describe = { "Endless high score, or 48 logs" },
        settingOf = { 0 },
        inProgress = { g -> (g.phase != Phase.MENU && g.phase != Phase.SELECT) || g.best > 0 || g.starTotal > 0 },
        subtitle = { g ->
            when (g.phase) {
                Phase.MENU -> if (g.best > 0) "Best ${g.best}" else "Two ways to play"
                Phase.SELECT -> "Level map"
                else -> if (g.mode == Mode.ENDLESS) "Endless · Score ${g.score}" else "Level ${g.level} · Score ${g.score}"
            }
        },
        levelLabel = { g ->
            when {
                g.phase == Phase.MENU || g.phase == Phase.SELECT -> "Log Throw"
                g.mode == Mode.ENDLESS -> "Endless"
                else -> "Level ${g.level}"
            }
        },
        create = { _ -> { factory.custom("logthrow", { seed -> LogThrow.newGame(seed) }) { it.seed.toString() } } },
        identity = { g ->
            when {
                g.phase == Phase.MENU || g.phase == Phase.SELECT -> "hub-${g.seed}"
                g.mode == Mode.ENDLESS -> "E${g.seed}-${g.attempt}"
                else -> "L${g.level}-${g.attempt}"
            }
        },
        outcome = { g ->
            when {
                g.mode == Mode.ENDLESS && g.phase == Phase.FAIL -> Outcome(Result.FINISHED, g.score.toLong())
                g.mode == Mode.LEVELS && g.phase == Phase.CLEAR -> Outcome(Result.WON, g.earned.toLong())
                g.mode == Mode.LEVELS && g.phase == Phase.FAIL -> Outcome(Result.LOST)
                else -> null
            }
        },
        carry = { old, fresh ->
            val banked = if (old.mode == Mode.ENDLESS) maxOf(old.best, old.score) else old.best
            fresh.copy(best = banked, stars = old.stars)
        },
    )
}

@Composable
fun LogThrowScreen(nav: NavController, vm: PlayViewModel<ThrowState>, factory: PuzzleFactory) {
    val measurer = rememberTextMeasurer()
    var paused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.flush() }
    }
    fun act(transform: (ThrowState) -> ThrowState?) {
        val before = vm.state.value.game
        vm.play(record = false, persist = false, transform = transform)
        val after = vm.state.value.game
        if (before != null && after != null && mark(before) != mark(after)) vm.flush()
    }
    PlayShell(nav, vm, GameId.LOG_THROW, remember(factory) { LogThrowSetup(factory) }, undoable = false, scroll = false, tight = true, tools = { g, _ ->
        val live = g.phase == Phase.AIM || g.phase == Phase.FLIGHT || g.phase == Phase.CLEAR
        if (live) ToolButton(if (paused) GameIcons.Pass else GameIcons.Timer, if (paused) "Resume" else "Pause", active = paused) {
            paused = !paused
            vm.flush()
        }
    }) { g, s ->
        var page by remember { mutableIntStateOf(0) }
        LaunchedEffect(g.phase, g.starTotal) {
            if (g.phase == Phase.SELECT) {
                val latest = (1..LogThrow.LEVEL_COUNT).lastOrNull { g.unlocked(it) } ?: 1
                page = (latest - 1) / LogThrowArt.LEVELS_PER_PAGE
            }
        }
        val moving = g.phase == Phase.AIM || g.phase == Phase.FLIGHT || g.phase == Phase.CLEAR || g.phase == Phase.FAIL
        val running = resumed && !paused && !s.busy && moving
        LaunchedEffect(running) {
            if (!running) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = ((now - last) / 1_000_000L).coerceIn(0L, 48L)
                        if (dt > 0L) act { it.advance(dt) }
                    }
                    last = now
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val swipe = if (g.phase == Phase.SELECT) {
                Modifier.pointerInput(page) {
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(acc) > 80f) page = (page + if (acc < 0f) 1 else -1).coerceIn(0, LogThrowArt.pageCount() - 1)
                            acc = 0f
                        },
                        onHorizontalDrag = { _, drag -> acc += drag },
                    )
                }
            } else {
                Modifier
            }
            Canvas(Modifier.fillMaxSize()
                .semantics { contentDescription = say(describe(g)) }
                .then(swipe)
                .pointerInput(paused, page) {
                    detectTapGestures { offset ->
                        val state = vm.state.value.game ?: return@detectTapGestures
                        val frame = LogThrowArt.frame(state, size.width.toFloat(), size.height.toFloat(), paused = paused, page = page)
                        val hot = LogThrowArt.hit(frame, offset.x, offset.y) ?: return@detectTapGestures
                        when (hot.id) {
                            "prev" -> page = (page - 1).coerceAtLeast(0)
                            "next" -> page = (page + 1).coerceAtMost(LogThrowArt.pageCount() - 1)
                            else -> act { cur ->
                                when (hot.id) {
                                    "endless" -> cur.beginEndless()
                                    "levels" -> cur.beginLevels()
                                    "modes" -> cur.toMenu()
                                    "map" -> cur.toLevels()
                                    "again" -> if (cur.mode == Mode.ENDLESS) cur.again() else cur.beginLevel(cur.level)
                                    "throw" -> cur.throwKnife()
                                    else -> hot.id.removePrefix("L").toIntOrNull()?.let(cur::beginLevel)
                                }
                            }
                        }
                    }
                }) {
                val frame = LogThrowArt.frame(g, size.width, size.height, paused = paused, page = page)
                frame.ops.forEach { draw(it, measurer) }
            }
        }
    }
}

private fun pathOf(pts: List<Float>): Path? {
    if (pts.size < 4) return null
    return Path().apply {
        moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
        close()
    }
}

private fun mark(g: ThrowState) = listOf(g.phase, g.mode, g.level, g.score, g.best, g.starTotal, g.attempt, g.thrown, g.earned, g.stuck.size)

private fun describe(g: ThrowState): String = when (g.phase) {
    Phase.MENU -> "Log Throw · Best ${g.best}"
    Phase.SELECT -> "Level map · ${g.starTotal} of ${LogThrow.LEVEL_COUNT * 3} stars"
    Phase.FAIL -> "Clang · Score ${g.score}"
    Phase.CLEAR -> "The log bursts · Score ${g.score}"
    else -> if (g.mode == Mode.ENDLESS) "Stage ${g.level} · Score ${g.score}" else "Level ${g.level} · Score ${g.score}"
}

private fun DrawScope.draw(op: DrawOp, measurer: androidx.compose.ui.text.TextMeasurer) {
    when (op) {
        is DrawOp.VGrad -> drawRect(
            Brush.verticalGradient(listOf(Color(op.top.toInt()), Color(op.bottom.toInt())), startY = op.y, endY = op.y + op.h),
            Offset(op.x, op.y), Size(op.w, op.h),
        )
        is DrawOp.Radial -> drawRect(
            Brush.radialGradient(listOf(Color(op.inner.toInt()), Color(op.outer.toInt())), center = Offset(op.cx, op.cy), radius = op.radius.coerceAtLeast(1f)),
            Offset(op.x, op.y), Size(op.w, op.h),
        )
        is DrawOp.Circle -> {
            val c = Color(op.color.toInt())
            if (op.stroke > 0f) drawCircle(c, op.r, Offset(op.x, op.y), style = Stroke(op.stroke))
            else drawCircle(c, op.r, Offset(op.x, op.y))
        }
        is DrawOp.RoundRect -> {
            val c = Color(op.color.toInt())
            val style = if (op.stroke > 0f) Stroke(op.stroke) else null
            if (style == null) drawRoundRect(c, Offset(op.x, op.y), Size(op.w, op.h), CornerRadius(op.radius))
            else drawRoundRect(c, Offset(op.x, op.y), Size(op.w, op.h), CornerRadius(op.radius), style = style)
        }
        is DrawOp.Line -> drawLine(Color(op.color.toInt()), Offset(op.x1, op.y1), Offset(op.x2, op.y2), op.stroke, StrokeCap.Round)
        is DrawOp.Clip -> pathOf(op.pts)?.let { path ->
            clipPath(path) { op.inner.forEach { draw(it, measurer) } }
        }
        is DrawOp.Poly -> pathOf(op.pts)?.let { path ->
            val c = Color(op.color.toInt())
            if (op.stroke > 0f) drawPath(path, c, style = Stroke(op.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            else drawPath(path, c)
        }
        is DrawOp.Shade -> pathOf(op.pts)?.let { path ->
            val end = if ((op.x1 - op.x0) * (op.x1 - op.x0) + (op.y1 - op.y0) * (op.y1 - op.y0) < 0.25f) {
                Offset(op.x0 + 1f, op.y0)
            } else {
                Offset(op.x1, op.y1)
            }
            drawPath(path, Brush.linearGradient(listOf(Color(op.c0.toInt()), Color(op.c1.toInt())), start = Offset(op.x0, op.y0), end = end))
        }
        is DrawOp.Wedge -> drawArc(
            Color(op.color.toInt()), op.startDeg, op.sweep, useCenter = true,
            topLeft = Offset(op.cx - op.r, op.cy - op.r), size = Size(op.r * 2, op.r * 2),
        )
        is DrawOp.Label -> {
            val style = TextStyle(
                color = Color(op.color.toInt()),
                fontSize = (op.size / density).coerceAtLeast(1f).sp,
                fontWeight = if (op.bold) FontWeight.Bold else FontWeight.Normal,
            )
            val layout = measurer.measure(say(op.text), style, overflow = TextOverflow.Visible, softWrap = false)
            val left = when (op.align) {
                0 -> op.x
                2 -> op.x - layout.size.width
                else -> op.x - layout.size.width / 2f
            }
            drawText(layout, topLeft = Offset(left, op.y - layout.firstBaseline))
        }
    }
}
