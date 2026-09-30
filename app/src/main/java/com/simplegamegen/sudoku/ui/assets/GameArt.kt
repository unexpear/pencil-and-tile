package com.simplegamegen.sudoku.ui.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** Small illustration for each game, drawn with the same assets used in play. Fill a ~64dp box. */
@Composable
fun GameArt(id: GameId, modifier: Modifier = Modifier) {
    val look = LocalGameLook.current
    val c = look.colors
    val measurer = rememberTextMeasurer()
    Box(modifier, contentAlignment = Alignment.Center) {
        when (id) {
            GameId.SOLITAIRE -> {
                PlayingCard(12 + 13, Modifier.offset((-14).dp, 2.dp).rotate(-14f), width = 30.dp)
                PlayingCard(11 + 26, Modifier.offset(14.dp, 2.dp).rotate(14f), width = 30.dp)
                PlayingCard(0, Modifier.offset(0.dp, (-2).dp), width = 30.dp)
            }
            GameId.MAHJONG -> {
                MahjongTile(31, Modifier.offset((-10).dp, 0.dp), width = 26.dp, depth = 3.dp)
                MahjongTile(4, Modifier.offset(12.dp, 4.dp), width = 26.dp, depth = 3.dp)
            }
            GameId.DOMINOES -> {
                DominoTile(6, 3, Modifier.offset((-10).dp, 0.dp).rotate(-12f), unit = 17.dp)
                DominoTile(3, 5, Modifier.offset(12.dp, 2.dp).rotate(10f), unit = 17.dp)
            }
            GameId.CHECKERS -> Canvas(Modifier.size(56.dp)) {
                val s = size.width / 3
                for (r in 0 until 3) for (k in 0 until 3) drawRect(if ((r + k) % 2 == 1) c.boardDark else c.boardLight,
                    Offset(k * s, r * s), Size(s, s))
                withTransform({ translate(0f, s); scale(1f / 3f, 1f / 3f, pivot = Offset.Zero) }) { drawChecker(c.playerOne, true) }
                withTransform({ translate(s * 1f, 0f); scale(1f / 3f, 1f / 3f, pivot = Offset.Zero) }) { drawChecker(c.playerTwo, false) }
                withTransform({ translate(s * 2f, s * 1f); scale(1f / 3f, 1f / 3f, pivot = Offset.Zero) }) { drawChecker(c.playerTwo, false) }
            }
            GameId.REVERSI -> Canvas(Modifier.size(52.dp)) {
                drawRoundRect(Color(0xFF2E7D4F), cornerRadius = CornerRadius(6f))
                val s = size.width / 2
                for (r in 0 until 2) for (k in 0 until 2) {
                    drawRect(Color(0xFF1B4D32), Offset(k * s, r * s), Size(s, s), style = Stroke(2f))
                    withTransform({ translate(k * s, r * s); scale(0.5f, 0.5f, pivot = Offset.Zero) }) { drawDisc(if ((r + k) % 2 == 0) -1 else 1) }
                }
            }
            GameId.SPIDER -> {
                PlayingCard(12, Modifier.offset((-12).dp, 0.dp).rotate(-10f), width = 30.dp)
                PlayingCard(11, Modifier.offset(0.dp, 0.dp), width = 30.dp)
                PlayingCard(10, Modifier.offset(12.dp, 0.dp).rotate(10f), width = 30.dp)
            }
            GameId.PYRAMID -> {
                PlayingCard(12 + 13, Modifier.offset(0.dp, (-14).dp), width = 24.dp)
                PlayingCard(5, Modifier.offset((-13).dp, 6.dp), width = 24.dp)
                PlayingCard(6 + 26, Modifier.offset(13.dp, 6.dp), width = 24.dp)
            }
            GameId.MEMORY -> {
                Canvas(Modifier.offset((-14).dp, 0.dp).size(26.dp, 36.dp)) { drawCardBack(c.cardBack, c.cardBackPattern) }
                MahjongTile(32, Modifier.offset(13.dp, 0.dp), width = 24.dp, depth = 3.dp)
            }
            GameId.DOTS -> Canvas(Modifier.size(54.dp)) {
                val s = size.width / 3
                drawRect(c.playerOne.copy(alpha = 0.5f), Offset(s * 0.5f, s * 0.5f), Size(s, s))
                val ink = c.text
                listOf(0.5f to 0.5f, 1.5f to 0.5f).forEach { (x, y) -> drawLine(ink, Offset(x * s, y * s), Offset((x + 1) * s, y * s), strokeWidth = 4f) }
                drawLine(ink, Offset(0.5f * s, 1.5f * s), Offset(1.5f * s, 1.5f * s), strokeWidth = 4f)
                drawLine(ink, Offset(0.5f * s, 0.5f * s), Offset(0.5f * s, 1.5f * s), strokeWidth = 4f)
                drawLine(ink, Offset(1.5f * s, 0.5f * s), Offset(1.5f * s, 2.5f * s), strokeWidth = 4f)
                for (r in 0..2) for (k in 0..2) drawCircle(ink, 3.5f, Offset((k + 0.5f) * s, (r + 0.5f) * s))
            }
            GameId.SPROUTS -> Canvas(Modifier.size(56.dp)) {
                val w = size.width
                val a = Offset(w * 0.22f, w * 0.7f); val b = Offset(w * 0.78f, w * 0.7f); val m = Offset(w * 0.5f, w * 0.28f)
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); quadraticBezierTo(w * 0.2f, w * 0.2f, m.x, m.y); quadraticBezierTo(w * 0.8f, w * 0.2f, b.x, b.y) }
                drawPath(path, c.text, style = Stroke(3f))
                drawLine(c.text, a, b, strokeWidth = 3f)
                listOf(a, b, m, Offset(w * 0.5f, w * 0.7f)).forEach { drawCircle(Color.White, 5f, it); drawCircle(c.text, 5f, it, style = Stroke(2f)) }
            }
            GameId.MAGNETS -> Canvas(Modifier.size(56.dp)) {
                val half = size.width / 2
                drawCircle(c.table, half)
                drawCircle(Color(0xFFD9C9A3), half, style = Stroke(3f))
                listOf(-0.4f to -0.2f, 0.3f to -0.35f, 0.1f to 0.4f, 0.45f to 0.3f).forEach { (x, y) ->
                    val o = Offset(half + x * half, half + y * half)
                    drawCircle(c.onTable.copy(alpha = 0.2f), half * 0.28f, o)
                    drawCircle(Color(0xFF3A3D43), half * 0.12f, o)
                }
            }
            GameId.G2048 -> Canvas(Modifier.size(54.dp)) {
                val gap = size.width * 0.05f; val cell = (size.width - gap * 3) / 2
                drawRoundRect(Color(0xFFBBADA0), cornerRadius = CornerRadius(gap * 2))
                listOf(2 to Color(0xFFEEE4DA), 8 to Color(0xFFF2B179), 64 to Color(0xFFF65E3B), 2048 to Color(0xFFEDC22E)).forEachIndexed { i, (v, col) ->
                    val o = Offset(gap + (i % 2) * (cell + gap), gap + (i / 2) * (cell + gap))
                    drawRoundRect(col, o, Size(cell, cell), CornerRadius(gap))
                    val l = measurer.measure(v.toString(), TextStyle(color = if (v < 8) Color(0xFF776E65) else Color.White, fontWeight = FontWeight.Bold,
                        fontSize = (cell * (if (v > 999) 0.26f else 0.42f) / density).sp))
                    drawText(l, topLeft = o + Offset((cell - l.size.width) / 2, (cell - l.size.height) / 2))
                }
            }
            GameId.CONNECT_FOUR -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFF1565C0), cornerRadius = CornerRadius(8f))
                val cols = 4
                val rows = 4
                val cw = size.width / cols
                val ch = size.height / rows
                val discs = mapOf((3 to 0) to 1, (3 to 1) to 1, (3 to 2) to 1, (2 to 1) to -1, (3 to 3) to -1)
                for (r in 0 until rows) for (c in 0 until cols) {
                    val center = Offset((c + 0.5f) * cw, (r + 0.5f) * ch)
                    drawCircle(Color(0xFF0D3A86), cw * 0.34f, center)
                    discs[r to c]?.let { who -> drawCircle(if (who > 0) Color(0xFFE53935) else Color(0xFFFDD835), cw * 0.26f, center) }
                }
            }
            GameId.BATTLESHIP -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFF0D47A1), cornerRadius = CornerRadius(8f))
                val n = 4
                val cw = size.width / n
                val ch = size.height / n
                for (r in 0 until n) for (c in 0 until n) {
                    val shade = if ((r + c) % 2 == 0) Color(0xFF1565C0) else Color(0xFF0E4C92)
                    drawRect(shade, Offset(c * cw, r * ch), Size(cw, ch))
                }
                drawRoundRect(Color(0xFFECEFF1), Offset(cw * 0.35f, ch * 2.28f), Size(cw * 2.5f, ch * 0.5f), CornerRadius(cw * 0.16f))
                drawCircle(Color(0xFFF7F4EF), cw * 0.14f, Offset(cw * 3.45f, ch * 0.55f))
                drawCircle(Color(0xFFE53935), cw * 0.16f, Offset(cw * 1.45f, ch * 2.52f))
            }
            GameId.MANCALA -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFF6D4C41), cornerRadius = CornerRadius(8f))
                drawRoundRect(Color(0xFF3E2723), Offset(size.width * 0.06f, size.height * 0.18f), Size(size.width * 0.16f, size.height * 0.64f), CornerRadius(8f))
                drawRoundRect(Color(0xFF3E2723), Offset(size.width * 0.78f, size.height * 0.18f), Size(size.width * 0.16f, size.height * 0.64f), CornerRadius(8f))
                val pit = size.width * 0.1f
                for (i in 0 until 4) {
                    drawCircle(Color(0xFFF3E5C4), pit * 0.45f, Offset(size.width * (0.32f + i * 0.12f), size.height * 0.34f))
                    drawCircle(Color(0xFFF3E5C4), pit * 0.45f, Offset(size.width * (0.32f + i * 0.12f), size.height * 0.66f))
                }
            }
            GameId.FIVE_ROW -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFC4A574), cornerRadius = CornerRadius(8f))
                val n = 5
                val cw = size.width / n
                for (i in 1 until n) {
                    drawLine(Color(0xFF3E2723), Offset(i * cw, cw * 0.4f), Offset(i * cw, size.height - cw * 0.4f), strokeWidth = 1.5f)
                    drawLine(Color(0xFF3E2723), Offset(cw * 0.4f, i * cw), Offset(size.width - cw * 0.4f, i * cw), strokeWidth = 1.5f)
                }
                listOf(1 to 2, 2 to 2, 3 to 2, 2 to 1, 2 to 3).forEachIndexed { i, (r, c) ->
                    drawCircle(if (i < 3) Color(0xFF1A1A1A) else Color(0xFFF7F4EF), cw * 0.28f, Offset((c + 0.5f) * cw, (r + 0.5f) * cw))
                }
            }
            GameId.WORD_LADDER -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFFFFDF7), cornerRadius = CornerRadius(8f))
                drawRoundRect(Color(0xFF2A2A2E), cornerRadius = CornerRadius(8f), style = Stroke(size.width * 0.03f))
                val cell = size.width / 4
                "COLD".forEachIndexed { i, ch ->
                    val layout = measurer.measure(ch.toString(), TextStyle(color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold, fontSize = (cell * 0.55f / density).sp))
                    drawText(layout, topLeft = Offset(i * cell + (cell - layout.size.width) / 2, size.height * 0.22f))
                }
                drawLine(Color(0xFF1B5E20), Offset(size.width * 0.2f, size.height * 0.72f), Offset(size.width * 0.8f, size.height * 0.72f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
            GameId.HONEYCOMB -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFFFF8E1), cornerRadius = CornerRadius(8f))
                val spots = listOf(0.32f to 0.28f, 0.68f to 0.28f, 0.22f to 0.55f, 0.5f to 0.55f, 0.78f to 0.55f, 0.32f to 0.8f, 0.68f to 0.8f)
                spots.forEachIndexed { i, (x, y) ->
                    drawCircle(if (i == 3) Color(0xFFE65100) else Color(0xFFFFE082), size.width * 0.11f, Offset(size.width * x, size.height * y))
                }
            }
            GameId.BRIDGES -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFE3F2FD), cornerRadius = CornerRadius(8f))
                drawLine(Color(0xFF0277BD), Offset(size.width * 0.22f, size.height * 0.35f), Offset(size.width * 0.78f, size.height * 0.35f), strokeWidth = 4f)
                drawCircle(Color(0xFF0277BD), size.width * 0.12f, Offset(size.width * 0.22f, size.height * 0.35f))
                drawCircle(Color(0xFF0277BD), size.width * 0.12f, Offset(size.width * 0.78f, size.height * 0.35f))
                drawCircle(Color(0xFF0277BD), size.width * 0.12f, Offset(size.width * 0.5f, size.height * 0.72f))
                drawLine(Color(0xFF0277BD), Offset(size.width * 0.5f, size.height * 0.35f), Offset(size.width * 0.5f, size.height * 0.72f), strokeWidth = 4f)
            }
            GameId.SLITHERLINK -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFECEFF1), cornerRadius = CornerRadius(8f))
                val step = size.width / 4
                drawLine(Color(0xFF37474F), Offset(step, step), Offset(step * 3, step), strokeWidth = 4f)
                drawLine(Color(0xFF37474F), Offset(step * 3, step), Offset(step * 3, step * 3), strokeWidth = 4f)
                drawLine(Color(0xFF37474F), Offset(step * 3, step * 3), Offset(step, step * 3), strokeWidth = 4f)
                drawLine(Color(0xFF37474F), Offset(step, step * 3), Offset(step, step), strokeWidth = 4f)
            }
            GameId.TOWERS -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFF3E5F5), cornerRadius = CornerRadius(8f))
                listOf(0.55f, 0.85f, 0.4f, 0.7f).forEachIndexed { i, h ->
                    val w = size.width / 5
                    drawRect(Color(0xFF6A1B9A), Offset(w * 0.6f + i * w, size.height * (1f - h) * 0.75f), Size(w * 0.7f, size.height * h * 0.75f))
                }
            }
            GameId.LIGHTS -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFF263238), cornerRadius = CornerRadius(8f))
                drawCircle(Color(0xFFFFE082), size.width * 0.16f, Offset(size.width * 0.35f, size.height * 0.4f))
                drawLine(Color(0xFFFFE082), Offset(size.width * 0.35f, size.height * 0.4f), Offset(size.width * 0.8f, size.height * 0.4f), strokeWidth = 3f)
                drawLine(Color(0xFFFFE082), Offset(size.width * 0.35f, size.height * 0.4f), Offset(size.width * 0.35f, size.height * 0.8f), strokeWidth = 3f)
            }
            GameId.CHESS -> Canvas(Modifier.size(56.dp)) {
                val s = size.width / 4
                for (r in 0 until 4) for (k in 0 until 4) {
                    drawRect(if ((r + k) % 2 == 0) c.boardLight else c.boardDark, Offset(k * s, r * s), Size(s, s))
                }
                drawCircle(c.playerTwo, s * 0.32f, Offset(s * 1.5f, s * 0.5f))
                drawCircle(c.playerOne, s * 0.28f, Offset(s * 2.5f, s * 3.5f))
            }
            GameId.FREECELL -> {
                PlayingCard(0, Modifier.offset((-14).dp, (-4).dp), width = 26.dp)
                PlayingCard(12, Modifier.offset(10.dp, 4.dp), width = 26.dp)
            }
            GameId.SLIDING_BLOCKS -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFE0F7FA), cornerRadius = CornerRadius(8f))
                drawRoundRect(Color(0xFF00838F), Offset(size.width * 0.12f, size.height * 0.38f), Size(size.width * 0.42f, size.height * 0.22f), CornerRadius(6f))
                drawRoundRect(Color(0xFF546E7A), Offset(size.width * 0.55f, size.height * 0.18f), Size(size.width * 0.22f, size.height * 0.42f), CornerRadius(6f))
                drawRoundRect(Color(0xFF90A4AE), Offset(size.width * 0.18f, size.height * 0.68f), Size(size.width * 0.5f, size.height * 0.18f), CornerRadius(6f))
                drawRect(Color(0xFF00838F), Offset(size.width * 0.88f, size.height * 0.38f), Size(size.width * 0.08f, size.height * 0.22f))
            }
            GameId.GO -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(c.boardLight, cornerRadius = CornerRadius(8f))
                val step = size.width / 4
                for (i in 1..3) {
                    drawLine(c.text.copy(alpha = 0.45f), Offset(step, step * i), Offset(step * 3, step * i), strokeWidth = 2f)
                    drawLine(c.text.copy(alpha = 0.45f), Offset(step * i, step), Offset(step * i, step * 3), strokeWidth = 2f)
                }
                drawCircle(c.playerOne, step * 0.32f, Offset(step * 1.5f, step * 2.2f))
                drawCircle(c.playerTwo, step * 0.32f, Offset(step * 2.5f, step * 1.4f))
                drawCircle(c.text, step * 0.32f, Offset(step * 2.5f, step * 1.4f), style = Stroke(2f))
            }
            GameId.LETTER_DRAW -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFFE0F2F1), cornerRadius = CornerRadius(8f))
                val cell = size.width / 5
                "WORD".forEachIndexed { i, ch ->
                    val layout = measurer.measure(ch.toString(), TextStyle(color = Color(0xFF00695C), fontWeight = FontWeight.Bold, fontSize = (cell * 0.7f / density).sp))
                    drawText(layout, topLeft = Offset(cell * 0.4f + i * cell + (cell - layout.size.width) / 2, size.height * 0.28f))
                }
                drawArc(Color(0xFF00695C), -90f, 270f, false, Offset(size.width * 0.72f, size.height * 0.62f), Size(size.width * 0.2f, size.width * 0.2f), style = Stroke(3f))
            }
            GameId.MASTERMIND -> Canvas(Modifier.size(56.dp)) {
                drawRoundRect(Color(0xFF2C2C34), cornerRadius = CornerRadius(8f))
                val pegs = listOf(0xFFD32F2F, 0xFF1565C0, 0xFFD32F2F, 0xFFF9A825)
                val slot = size.width / 5
                pegs.forEachIndexed { i, col ->
                    val center = Offset(slot * (i + 0.85f), size.height * 0.38f)
                    drawCircle(Color(0xFF141418), slot * 0.34f, center)
                    drawCircle(Color(col), slot * 0.26f, center)
                }
                val key = Offset(size.width * 0.78f, size.height * 0.74f)
                drawCircle(Color(0xFFF4F1EA), slot * 0.16f, key)
                drawCircle(Color(0xFFF4F1EA), slot * 0.16f, key + Offset(slot * 0.55f, 0f), style = Stroke(slot * 0.08f))
            }
            GameId.TETRAS -> Canvas(Modifier.size(56.dp)) {
                val s = size.width / 5
                drawRect(Color(0xFF15161B))
                val cells = listOf(Triple(0, 4, 0xFF3FC6E0), Triple(1, 4, 0xFF3FC6E0), Triple(2, 4, 0xFF3FC6E0), Triple(3, 4, 0xFF3FC6E0),
                    Triple(4, 4, 0xFFF08A3C), Triple(4, 3, 0xFFF08A3C), Triple(3, 3, 0xFFF08A3C), Triple(0, 3, 0xFF5BBF5B), Triple(1, 3, 0xFF5BBF5B),
                    Triple(1, 2, 0xFF5BBF5B), Triple(2, 2, 0xFF5BBF5B), Triple(2, 0, 0xFFA066D3), Triple(1, 1, 0xFFA066D3), Triple(2, 1, 0xFFA066D3), Triple(3, 1, 0xFFA066D3))
                cells.forEach { (x, y, col) ->
                    drawRect(Color(col), Offset(x * s + 1, y * s + 1), Size(s - 2, s - 2))
                    drawRect(Color.White.copy(alpha = 0.3f), Offset(x * s + 1, y * s + 1), Size(s - 2, s * 0.15f))
                }
            }
            GameId.MINES -> Canvas(Modifier.size(54.dp)) {
                val s = size.width / 3
                for (r in 0 until 3) for (k in 0 until 3) {
                    val o = Offset(k * s, r * s)
                    val open = r == 2 || (r == 1 && k == 0)
                    drawRect(if (open) c.surface else c.surfaceAlt, o, Size(s, s))
                    drawRect(c.outline, o, Size(s, s), style = Stroke(1.5f))
                }
                withTransform({ translate(s * 2, 0f); scale(1f / 3f, 1f / 3f, pivot = Offset.Zero) }) { drawFlag() }
                withTransform({ translate(s, s); scale(1f / 3f, 1f / 3f, pivot = Offset.Zero) }) { drawMine() }
                val layout = measurer.measure("2", TextStyle(color = mineNumberColor(2, look.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                drawText(layout, topLeft = Offset(s * 0.5f - layout.size.width / 2, s * 1.5f - layout.size.height / 2))
            }
            else -> Canvas(Modifier.fillMaxSize()) {
                val side = minOf(size.width, size.height) * 0.86f
                val o = Offset((size.width - side) / 2, (size.height - side) / 2)
                drawRoundRect(Color(0xFFFFFDF7), o, Size(side, side), CornerRadius(side * 0.08f))
                drawRoundRect(Color(0xFF2A2A2E), o, Size(side, side), CornerRadius(side * 0.08f), style = Stroke(side * 0.03f))
                val ink = Color(0xFF2A2A2E)
                fun letter(ch: String, x: Int, y: Int, n: Int, color: Color = ink) {
                    val cell = side / n
                    val layout = measurer.measure(ch, TextStyle(color = color, fontSize = (cell * 0.62f / density).sp, fontWeight = FontWeight.Bold))
                    drawText(layout, topLeft = o + Offset(x * cell + cell / 2 - layout.size.width / 2, y * cell + cell / 2 - layout.size.height / 2))
                }
                when (id) {
                    GameId.SUDOKU -> {
                        for (k in 1 until 3) {
                            drawLine(ink, o + Offset(side * k / 3, 0f), o + Offset(side * k / 3, side), strokeWidth = side * 0.025f)
                            drawLine(ink, o + Offset(0f, side * k / 3), o + Offset(side, side * k / 3), strokeWidth = side * 0.025f)
                        }
                        letter("5", 0, 0, 3); letter("3", 2, 0, 3); letter("7", 1, 1, 3, c.accent); letter("9", 0, 2, 3); letter("1", 2, 2, 3)
                    }
                    GameId.KILLER -> {
                        for (k in 1 until 3) {
                            drawLine(ink, o + Offset(side * k / 3, 0f), o + Offset(side * k / 3, side), strokeWidth = side * 0.02f)
                            drawLine(ink, o + Offset(0f, side * k / 3), o + Offset(side, side * k / 3), strokeWidth = side * 0.02f)
                        }
                        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(side * 0.05f, side * 0.04f))
                        val i = side * 0.05f; val t = side / 3
                        drawRect(c.accent, o + Offset(i, i), Size(2 * t - 2 * i, t - 2 * i), style = Stroke(side * 0.025f, pathEffect = dash))
                        drawRect(c.accent, o + Offset(2 * t + i, i), Size(t - 2 * i, 2 * t - 2 * i), style = Stroke(side * 0.025f, pathEffect = dash))
                        drawRect(c.accent, o + Offset(i, t + i), Size(2 * t - 2 * i, 2 * t - 2 * i), style = Stroke(side * 0.025f, pathEffect = dash))
                        fun tiny(txt: String, x: Float, y: Float) {
                            val l = measurer.measure(txt, TextStyle(color = ink, fontSize = (side * 0.13f / density).sp, fontWeight = FontWeight.Bold))
                            drawText(l, topLeft = o + Offset(x, y))
                        }
                        tiny("11", i * 1.6f, i * 1.3f); tiny("9", 2 * t + i * 1.6f, i * 1.3f); tiny("25", i * 1.6f, t + i * 1.3f)
                        letter("7", 2, 2, 3, c.accent)
                    }
                    GameId.SAMURAI -> {
                        val s = side * 0.44f
                        listOf(0f to 0f, side - s to 0f, (side - s) / 2 to (side - s) / 2, 0f to side - s, side - s to side - s).forEach { (x, y) ->
                            drawRect(Color(0xFFFFFDF7), o + Offset(x, y), Size(s, s))
                            drawRect(ink, o + Offset(x, y), Size(s, s), style = Stroke(side * 0.025f))
                            for (k in 1 until 3) {
                                drawLine(ink.copy(alpha = 0.5f), o + Offset(x + s * k / 3, y), o + Offset(x + s * k / 3, y + s), strokeWidth = 1.2f)
                                drawLine(ink.copy(alpha = 0.5f), o + Offset(x, y + s * k / 3), o + Offset(x + s, y + s * k / 3), strokeWidth = 1.2f)
                            }
                        }
                        drawRect(c.accent.copy(alpha = 0.35f), o + Offset((side - s) / 2, (side - s) / 2), Size(s, s))
                    }
                    GameId.KENKEN -> {
                        val t = side / 3
                        for (k in 1 until 3) {
                            drawLine(ink.copy(alpha = 0.4f), o + Offset(t * k, 0f), o + Offset(t * k, side), strokeWidth = 1.2f)
                            drawLine(ink.copy(alpha = 0.4f), o + Offset(0f, t * k), o + Offset(side, t * k), strokeWidth = 1.2f)
                        }
                        drawLine(ink, o + Offset(t, 0f), o + Offset(t, 2 * t), strokeWidth = side * 0.04f)
                        drawLine(ink, o + Offset(0f, 2 * t), o + Offset(2 * t, 2 * t), strokeWidth = side * 0.04f)
                        drawLine(ink, o + Offset(2 * t, t), o + Offset(2 * t, side), strokeWidth = side * 0.04f)
                        drawLine(ink, o + Offset(t, t), o + Offset(side, t), strokeWidth = side * 0.04f)
                        fun tiny(txt: String, x: Int, y: Int) {
                            val l = measurer.measure(txt, TextStyle(color = ink, fontSize = (side * 0.12f / density).sp, fontWeight = FontWeight.Bold))
                            drawText(l, topLeft = o + Offset(x * t + side * 0.03f, y * t + side * 0.01f))
                        }
                        tiny("6×", 0, 0); tiny("2−", 1, 0); tiny("5+", 0, 2); tiny("3", 2, 1)
                        letter("3", 2, 1, 3, c.accent)
                    }
                    GameId.KAKURO -> {
                        val t = side / 3
                        val block = Color(0xFF2A2A2E)
                        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2).forEach { (x, y) -> drawRect(block, o + Offset(x * t, y * t), Size(t, t)) }
                        listOf(1 to 0, 2 to 0, 0 to 1, 0 to 2).forEach { (x, y) ->
                            drawLine(Color(0xFFBDBDBD), o + Offset(x * t, y * t), o + Offset((x + 1) * t, (y + 1) * t), strokeWidth = 1.5f)
                        }
                        for (k in 1 until 3) {
                            drawLine(ink.copy(alpha = 0.5f), o + Offset(t * k, t), o + Offset(t * k, side), strokeWidth = 1.2f)
                            drawLine(ink.copy(alpha = 0.5f), o + Offset(t, t * k), o + Offset(side, t * k), strokeWidth = 1.2f)
                        }
                        fun clue(txt: String, x: Int, y: Int, down: Boolean) {
                            val l = measurer.measure(txt, TextStyle(color = Color.White, fontSize = (side * 0.11f / density).sp, fontWeight = FontWeight.Bold))
                            val at = if (down) Offset(x * t + t * 0.08f, (y + 1) * t - l.size.height) else Offset((x + 1) * t - l.size.width - t * 0.06f, y * t + t * 0.04f)
                            drawText(l, topLeft = o + at)
                        }
                        clue("4", 1, 0, true); clue("11", 2, 0, true); clue("3", 0, 1, false); clue("12", 0, 2, false)
                        letter("1", 1, 1, 3); letter("2", 2, 1, 3); letter("3", 1, 2, 3); letter("9", 2, 2, 3, c.accent)
                    }
                    GameId.FUTOSHIKI -> {
                        val t = side / 3
                        for (r in 0 until 2) for (k in 0 until 2) {
                            val o2 = o + Offset(t * 0.35f + k * t * 1.35f, t * 0.35f + r * t * 1.35f)
                            drawRoundRect(ink, o2, Size(t, t), CornerRadius(4f), style = Stroke(side * 0.025f))
                        }
                        letter("3", 0, 0, 3); letter("1", 2, 2, 3, c.accent)
                        val l = measurer.measure("<", TextStyle(color = ink, fontSize = (side * 0.2f / density).sp, fontWeight = FontWeight.Bold))
                        drawText(l, topLeft = o + Offset(t * 1.35f + t * 0.35f - l.size.width / 2f - t * 0.17f, t * 0.85f - l.size.height / 2f))
                        val v = measurer.measure("∨", TextStyle(color = ink, fontSize = (side * 0.2f / density).sp, fontWeight = FontWeight.Bold))
                        drawText(v, topLeft = o + Offset(t * 0.85f - v.size.width / 2f, t * 1.53f - v.size.height / 2f))
                    }
                    GameId.CRYPTOGRAM -> {
                        val plain = "CODE"; val coded = "XRSQ"
                        plain.forEachIndexed { i, ch -> letter(ch.toString(), i, 1, 4, if (i < 2) c.accent else ink) }
                        coded.forEachIndexed { i, ch ->
                            val l = measurer.measure(ch.toString(), TextStyle(color = ink.copy(alpha = 0.55f), fontSize = (side * 0.13f / density).sp))
                            drawText(l, topLeft = o + Offset(i * side / 4 + side / 8 - l.size.width / 2, side * 0.62f))
                        }
                        repeat(4) { i -> drawLine(ink, o + Offset(i * side / 4 + side * 0.04f, side * 0.58f), o + Offset((i + 1) * side / 4 - side * 0.04f, side * 0.58f), strokeWidth = 2f) }
                    }
                    GameId.SCRAMBLE -> {
                        val t = side / 4
                        "WROD".forEachIndexed { i, ch ->
                            drawRoundRect(c.accent, o + Offset(i * t + t * 0.08f, side * 0.1f), Size(t * 0.84f, t * 1.1f), CornerRadius(4f))
                            val l = measurer.measure(ch.toString(), TextStyle(color = c.onAccent, fontSize = (t * 0.6f / density).sp, fontWeight = FontWeight.Bold))
                            drawText(l, topLeft = o + Offset(i * t + (t - l.size.width) / 2, side * 0.1f + (t * 1.1f - l.size.height) / 2))
                        }
                        letter("W", 0, 3, 4); letter("O", 1, 3, 4); letter("R", 2, 3, 4); letter("D", 3, 3, 4)
                    }
                    GameId.ACROSTIC -> {
                        val t = side / 4
                        drawRect(c.highlight.copy(alpha = 0.45f), o + Offset(side * 0.03f, side * 0.03f), Size(t * 0.95f, side - side * 0.06f))
                        listOf("SUN", "TREE", "AIR", "RAIN").forEachIndexed { r, w -> w.forEachIndexed { k, ch -> letter(ch.toString(), k, r, 4, if (k == 0) c.accent else ink) } }
                    }
                    GameId.CROSSWORD -> {
                        val cell = side / 5
                        drawRect(c.accent.copy(alpha = 0.24f), o + Offset(0f, cell), Size(side, cell))
                        for ((x, y) in listOf(0 to 0, 1 to 0, 4 to 0, 0 to 2, 4 to 2, 0 to 4, 3 to 4, 4 to 4))
                            drawRect(ink, o + Offset(x * cell, y * cell), Size(cell, cell))
                        for (k in 1 until 5) {
                            drawLine(ink.copy(alpha = 0.55f), o + Offset(cell * k, 0f), o + Offset(cell * k, side), strokeWidth = side * 0.015f)
                            drawLine(ink.copy(alpha = 0.55f), o + Offset(0f, cell * k), o + Offset(side, cell * k), strokeWidth = side * 0.015f)
                        }
                        "WORDS".forEachIndexed { x, ch -> letter(ch.toString(), x, 1, 5) }
                        letter("T", 2, 0, 5); letter("E", 2, 2, 5); letter("E", 2, 3, 5)
                        for ((x, y, number) in listOf(Triple(2, 0, "1"), Triple(0, 1, "2"), Triple(1, 2, "3"))) {
                            val label = measurer.measure(number, TextStyle(color = ink, fontSize = (cell * 0.28f / density).sp))
                            drawText(label, topLeft = o + Offset(x * cell + cell * 0.08f, y * cell))
                        }
                        // A pencil rests across the unfinished lower corner.
                        fun p(x: Float, y: Float) = o + Offset(side * x, side * y)
                        drawLine(ink.copy(alpha = 0.2f), p(.57f, .94f), p(.98f, .51f), side * .14f, StrokeCap.Round)
                        drawLine(Color(0xFFE8B54B), p(.57f, .90f), p(.96f, .49f), side * .12f)
                        drawLine(Color(0xFFFFD77A), p(.57f, .88f), p(.94f, .49f), side * .035f)
                        drawLine(Color(0xFFC97472), p(.91f, .54f), p(.98f, .47f), side * .12f, StrokeCap.Round)
                        drawLine(Color(0xFFE5C99B), p(.57f, .90f), p(.50f, .97f), side * .08f)
                        drawLine(ink, p(.52f, .95f), p(.49f, .98f), side * .04f, StrokeCap.Round)
                    }
                    GameId.NONOGRAM -> {
                        // A small heart painted from clues.
                        val cell = side / 5
                        val heart = listOf("01010", "11111", "11111", "01110", "00100")
                        heart.forEachIndexed { r, row -> row.forEachIndexed { k, ch ->
                            if (ch == '1') drawRect(c.accent, o + Offset(k * cell, r * cell), Size(cell + 0.5f, cell + 0.5f))
                            else { val a = o + Offset(k * cell + cell * .32f, r * cell + cell * .32f); val d = cell * .36f
                                drawLine(ink.copy(alpha = .45f), a, a + Offset(d, d), strokeWidth = 2f); drawLine(ink.copy(alpha = .45f), a + Offset(d, 0f), a + Offset(0f, d), strokeWidth = 2f) }
                        } }
                        for (k in 1 until 5) {
                            drawLine(ink.copy(alpha = .25f), o + Offset(cell * k, 0f), o + Offset(cell * k, side), strokeWidth = 1f)
                            drawLine(ink.copy(alpha = .25f), o + Offset(0f, cell * k), o + Offset(side, cell * k), strokeWidth = 1f)
                        }
                    }
                    GameId.HITORI -> {
                        val cell = side / 3
                        val nums = "213" + "132" + "311"
                        nums.forEachIndexed { i, ch ->
                            val x = i % 3; val y = i / 3
                            val shaded = i == 1 || i == 8
                            if (shaded) drawRect(ink, o + Offset(x * cell, y * cell), Size(cell, cell))
                            if (i == 4) drawCircle(c.accent, cell * .36f, o + Offset(x * cell + cell / 2, y * cell + cell / 2), style = Stroke(2.5f))
                            letter(ch.toString(), x, y, 3, if (shaded) Color(0xFFFFFDF7) else ink)
                        }
                        for (k in 1 until 3) {
                            drawLine(ink, o + Offset(cell * k, 0f), o + Offset(cell * k, side), strokeWidth = 1.5f)
                            drawLine(ink, o + Offset(0f, cell * k), o + Offset(side, cell * k), strokeWidth = 1.5f)
                        }
                    }
                    GameId.CODE_CRACKER -> {
                        val cell = side / 4
                        for ((x, y) in listOf(0 to 0, 3 to 0, 0 to 2, 2 to 3, 3 to 3)) drawRect(ink, o + Offset(x * cell, y * cell), Size(cell, cell))
                        for (k in 1 until 4) {
                            drawLine(ink, o + Offset(cell * k, 0f), o + Offset(cell * k, side), strokeWidth = 1.5f)
                            drawLine(ink, o + Offset(0f, cell * k), o + Offset(side, cell * k), strokeWidth = 1.5f)
                        }
                        val small = TextStyle(color = ink.copy(alpha = .6f), fontSize = (cell * .26f / density).sp)
                        for ((x, y, n) in listOf(Triple(1, 0, "7"), Triple(2, 0, "3"), Triple(0, 1, "12"), Triple(1, 1, "7"), Triple(2, 1, "5"),
                            Triple(3, 1, "9"), Triple(1, 2, "3"), Triple(2, 2, "20"), Triple(3, 2, "5"), Triple(0, 3, "8"), Triple(1, 3, "9"))) {
                            drawText(measurer.measure(n, small), topLeft = o + Offset(x * cell + 3f, y * cell + 1f))
                        }
                        letter("A", 1, 1, 4, c.accent); letter("A", 1, 0, 4, c.accent)
                    }
                    GameId.DROPQUOTE -> {
                        val cell = side / 4
                        "TOEH".forEachIndexed { i, ch -> letter(ch.toString(), i, 0, 4, ink.copy(alpha = .55f)) }
                        drawLine(ink, o + Offset(0f, cell * 1.1f), o + Offset(side, cell * 1.1f), strokeWidth = 2f)
                        "THE".forEachIndexed { i, ch -> letter(ch.toString(), i, 2, 4, c.accent) }
                        drawRect(ink, o + Offset(cell * 3, cell * 2), Size(cell, cell))
                        for (k in 1 until 4) drawLine(ink.copy(alpha = .4f), o + Offset(cell * k, cell * 2), o + Offset(cell * k, side), strokeWidth = 1.5f)
                        drawLine(ink.copy(alpha = .4f), o + Offset(0f, cell * 3), o + Offset(side, cell * 3), strokeWidth = 1.5f)
                        drawLine(ink.copy(alpha = .4f), o + Offset(0f, cell * 2), o + Offset(side, cell * 2), strokeWidth = 1.5f)
                        drawLine(c.accent, o + Offset(cell * 1.5f, cell * 1.25f), o + Offset(cell * 1.5f, cell * 1.8f), strokeWidth = 2.5f)
                    }
                    GameId.THREADS -> {
                        // A found band on top, then a board of word tiles with four picked.
                        val cell = side / 4
                        val pad = cell * 0.1f
                        drawRoundRect(c.accent.copy(alpha = 0.85f), o + Offset(pad, pad), Size(side - pad * 2, cell - pad * 2), CornerRadius(cell * 0.18f))
                        for (y in 1 until 4) for (x in 0 until 4) {
                            val on = (x + y) % 3 == 0
                            drawRoundRect(if (on) c.highlight else ink.copy(alpha = 0.14f), o + Offset(x * cell + pad, y * cell + pad),
                                Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.18f))
                        }
                    }
                    GameId.FIVE_LETTERS -> {
                        // Three guesses of five squares: right spots filled, near misses dotted.
                        val cell = side / 5
                        val pad = cell * 0.1f
                        val rows = listOf("SAAAN", "AARRA", "RRRRR")
                        rows.forEachIndexed { r, row ->
                            row.forEachIndexed { i, m ->
                                val at = o + Offset(i * cell + pad, (r + 1) * cell + pad)
                                val fill = when (m) { 'R' -> c.success; 'S' -> c.highlight; else -> ink.copy(alpha = 0.16f) }
                                drawRoundRect(fill, at, Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.12f))
                                if (m == 'S') drawCircle(ink, cell * 0.08f, at + Offset(cell * 0.62f, cell * 0.18f))
                            }
                        }
                    }
                    GameId.WORD_MEANING -> {
                        // A word card with a speech bubble underneath.
                        val u = side / 10
                        drawRoundRect(c.accent.copy(alpha = 0.18f), o + Offset(u, u), Size(u * 8, u * 3.2f), CornerRadius(u * 0.8f))
                        letter("Aa", 1, 0, 3, c.accent)
                        drawRoundRect(ink.copy(alpha = 0.14f), o + Offset(u, u * 5), Size(u * 8, u * 3.2f), CornerRadius(u * 1.2f))
                        drawCircle(ink.copy(alpha = 0.14f), u * 0.6f, o + Offset(u * 2.4f, u * 8.9f))
                        for (k in 0..2) drawLine(ink.copy(alpha = 0.5f), o + Offset(u * 2, u * (5.9f + k * 0.8f)), o + Offset(u * (8 - k * 1.5f), u * (5.9f + k * 0.8f)), strokeWidth = u * 0.3f)
                    }
                    GameId.BLOTWORDS -> {
                        // A letter grid half inked: rounded blots where words were written, a word still waiting.
                        val cell = side / 4
                        val pad = cell * 0.08f
                        val blots = setOf(0, 1, 2, 5, 9, 15)
                        val letters = "   KD RIU ZEVUM "
                        for (i in 0 until 16) {
                            val x = i % 4; val y = i / 4
                            if (i in blots) drawRoundRect(ink, o + Offset(x * cell + pad, y * cell + pad), Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.32f))
                            else if (letters[i] != ' ') letter(letters[i].toString(), x, y, 4, if (y == 3) c.accent else ink)
                        }
                    }
                    GameId.LETTER_SPRAWL -> {
                        // Letter tiles with a word traced through them, bending diagonally.
                        val cell = side / 4
                        val pad = cell * 0.08f
                        val path = listOf(0 to 1, 1 to 1, 2 to 2, 3 to 2)
                        for (y in 0 until 4) for (x in 0 until 4) {
                            val on = (x to y) in path
                            drawRoundRect(if (on) c.accent.copy(alpha = 0.9f) else ink.copy(alpha = 0.12f), o + Offset(x * cell + pad, y * cell + pad),
                                Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.22f))
                        }
                        for (k in 1 until path.size) {
                            val (ax, ay) = path[k - 1]; val (bx, by) = path[k]
                            drawLine(Color.White.copy(alpha = 0.85f), o + Offset((ax + 0.5f) * cell, (ay + 0.5f) * cell), o + Offset((bx + 0.5f) * cell, (by + 0.5f) * cell),
                                strokeWidth = cell * 0.12f, cap = StrokeCap.Round)
                        }
                        "SPRA".forEachIndexed { i, ch -> letter(ch.toString(), path[i].first, path[i].second, 4, Color.White) }
                    }
                    GameId.WORDSWORN -> {
                        // A friendly monster squaring up to a letter tile.
                        with(com.simplegamegen.sudoku.ui.screens.MonsterArt) {
                            drawMonsterAt(com.simplegamegen.sudoku.wordplay.Monster.TYPO_IMP,
                                androidx.compose.ui.geometry.Rect(o + Offset(side * 0.3f, 0f), Size(side * 0.7f, side * 0.7f)))
                        }
                        drawRoundRect(Color(0xFFFFF6E0), o + Offset(side * 0.04f, side * 0.52f), Size(side * 0.36f, side * 0.44f), CornerRadius(side * 0.06f))
                        drawRoundRect(Color(0xFFB8A57E), o + Offset(side * 0.04f, side * 0.52f), Size(side * 0.36f, side * 0.44f), CornerRadius(side * 0.06f),
                            style = Stroke(side * 0.02f))
                        drawText(measurer.measure("W", TextStyle(color = Color(0xFF2A2418), fontSize = (side * 0.26f / density).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)),
                            topLeft = o + Offset(side * 0.1f, side * 0.56f))
                    }
                    GameId.LONE_LETTER -> {
                        // A letter die above a list of answers being written.
                        val die = side * 0.46f
                        drawRoundRect(c.accent, o + Offset(side * 0.04f, side * 0.04f), Size(die, die), CornerRadius(die * 0.2f))
                        val layout = measurer.measure("B", TextStyle(color = Color.White, fontSize = (die * 0.7f / density).sp, fontWeight = FontWeight.Black))
                        drawText(layout, topLeft = o + Offset(side * 0.04f + die / 2 - layout.size.width / 2, side * 0.04f + die / 2 - layout.size.height / 2))
                        for (k in 0 until 3) {
                            val y = side * (0.62f + k * 0.14f)
                            drawCircle(ink, side * 0.03f, o + Offset(side * 0.1f, y))
                            drawLine(ink.copy(alpha = if (k == 2) 0.3f else 0.8f), o + Offset(side * 0.2f, y), o + Offset(side * (0.9f - k * 0.12f), y),
                                strokeWidth = side * 0.035f, cap = StrokeCap.Round)
                        }
                        // The pencil.
                        drawLine(ink, o + Offset(side * 0.62f, side * 0.4f), o + Offset(side * 0.9f, side * 0.1f), strokeWidth = side * 0.07f, cap = StrokeCap.Round)
                    }
                    GameId.WORD_QUILT -> {
                        // Patches of colour sewn together, each with its letters.
                        val cell = side / 3
                        val colors = listOf(c.accent.copy(alpha = 0.35f), c.highlight.copy(alpha = 0.7f), ink.copy(alpha = 0.12f))
                        val patchOf = listOf(0, 0, 1, 2, 1, 1, 2, 2, 0)
                        for (i in 0 until 9) drawRect(colors[patchOf[i]], o + Offset((i % 3) * cell, (i / 3) * cell), Size(cell, cell))
                        "CATAREBET".forEachIndexed { i, ch -> letter(ch.toString(), i % 3, i / 3, 3) }
                        drawRect(ink, o, Size(side, side), style = Stroke(side * 0.03f))
                        for (k in 1 until 3) {
                            drawLine(ink.copy(alpha = 0.6f), o + Offset(cell * k, 0f), o + Offset(cell * k, side), strokeWidth = 2f)
                            drawLine(ink.copy(alpha = 0.6f), o + Offset(0f, cell * k), o + Offset(side, cell * k), strokeWidth = 2f)
                        }
                    }
                    GameId.LETTERFALL -> {
                        // Side-by-side tiles spelling a word, with the row above still falling into place.
                        val cell = side / 4
                        val pad = cell * 0.08f
                        val word = listOf(0 to 2, 1 to 2, 2 to 2)
                        for (y in 0 until 4) for (x in 0 until 4) {
                            val on = (x to y) in word
                            val falling = y == 1 && x < 3
                            val dy = if (falling) -cell * 0.22f else 0f
                            drawRoundRect(
                                if (on) c.accent.copy(alpha = 0.9f) else ink.copy(alpha = if (falling) 0.22f else 0.12f),
                                o + Offset(x * cell + pad, y * cell + pad + dy),
                                Size(cell - pad * 2, cell - pad * 2), CornerRadius(cell * 0.22f),
                            )
                        }
                        for (k in 1 until word.size) {
                            val (ax, ay) = word[k - 1]
                            val (bx, by) = word[k]
                            drawLine(
                                Color.White.copy(alpha = 0.85f),
                                o + Offset((ax + 0.5f) * cell, (ay + 0.5f) * cell),
                                o + Offset((bx + 0.5f) * cell, (by + 0.5f) * cell),
                                strokeWidth = cell * 0.12f, cap = StrokeCap.Round,
                            )
                        }
                        "CAT".forEachIndexed { i, ch -> letter(ch.toString(), i, 2, 4, Color.White) }
                    }
                    GameId.WORD_SEARCH -> {
                        val cell = side / 4
                        drawRoundRect(look.colors.highlight.copy(alpha = 0.55f), o + Offset(cell * 0.12f, cell * 1.12f),
                            Size(side - cell * 0.24f, cell * 0.76f), CornerRadius(cell * 0.38f))
                        "QKEPSTARXLOM".forEachIndexed { i, ch -> letter(ch.toString(), i % 4, i / 4, 4) }
                        letter("W", 0, 3, 4); letter("I", 1, 3, 4); letter("N", 2, 3, 4); letter("D", 3, 3, 4)
                    }
                    else -> {
                        val wood = Color(0xFF8B5A34)
                        fun p(x: Float, y: Float) = o + Offset(x * side, y * side)
                        drawLine(wood, p(.2f, .88f), p(.62f, .88f), strokeWidth = side * .06f)
                        drawLine(wood, p(.3f, .88f), p(.3f, .12f), strokeWidth = side * .06f)
                        drawLine(wood, p(.3f, .14f), p(.66f, .14f), strokeWidth = side * .06f)
                        drawLine(Color(0xFFB08850), p(.66f, .14f), p(.66f, .26f), strokeWidth = side * .025f)
                        drawCircle(ink, side * .07f, p(.66f, .33f), style = Stroke(side * .03f))
                        drawLine(ink, p(.66f, .4f), p(.66f, .6f), strokeWidth = side * .03f)
                        drawLine(ink, p(.66f, .46f), p(.56f, .54f), strokeWidth = side * .03f)
                        drawLine(ink, p(.66f, .46f), p(.76f, .54f), strokeWidth = side * .03f)
                        drawLine(ink, p(.66f, .6f), p(.58f, .72f), strokeWidth = side * .03f)
                        drawLine(ink, p(.66f, .6f), p(.74f, .72f), strokeWidth = side * .03f)
                    }
                }
            }
        }
    }
}
