package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons on a 24-unit grid. `F:` prefixes a filled path; others are 2-unit strokes. */
private fun icon(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (raw in paths) {
        val filled = raw.startsWith("F:")
        b.addPath(
            pathData = addPathNodes(raw.removePrefix("F:")),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = if (filled) null else SolidColor(Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

object GameIcons {
    val Back = icon("back", "M19 12H5", "M11 18l-6-6 6-6")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Hint = icon("hint", "M9 18h6", "M10 21h4", "M12 3a6 6 0 0 0-3.8 10.6c.6.6.8 1.3.8 2.4h6c0-1.1.2-1.8.8-2.4A6 6 0 0 0 12 3z")
    val Restart = icon("restart", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val More = icon("more", "F:" + circle(12f, 5f, 1.6f), "F:" + circle(12f, 12f, 1.6f), "F:" + circle(12f, 19f, 1.6f))
    val Rules = icon("rules", circle(12f, 12f, 9f), "M12 11v5", "F:" + circle(12f, 7.8f, 1.2f))
    val Save = icon("save", "M5 4h11l3 3v13H5z", "M8 4v5h7V4", "M8 20v-6h8v6")
    val Palette = icon("palette", "M12 21a9 9 0 1 1 9-9c0 2.5-2 3.5-3.5 3.5H15a2 2 0 0 0-1.2 3.6A1.5 1.5 0 0 1 12 21z",
        "F:" + circle(7.5f, 11f, 1.3f), "F:" + circle(10.5f, 7f, 1.3f), "F:" + circle(15f, 7.5f, 1.3f))
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val Stats = icon("stats", "M4 20V11", "M10 20V5", "M16 20v-8", "M21 20H3")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val Delete = icon("delete", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3")
    val Copy = icon("copy", "M8 8h11v11H8z", "M16 8V5H5v11h3")
    val Paste = icon("paste", "M9 4h6v3H9z", "M9 5H6v16h12V5h-3", "M9 13h6", "M9 17h4")
    val Eraser = icon("eraser", "M4 15.5l8.5-8.5 6 6L12 19.5H8z", "M10 19.5h10")
    val Pencil = icon("pencil", "M4 20h4L19 9l-4-4L4 16z")
    val Timer = icon("timer", circle(12f, 13f, 8f), "M12 9v4l2.5 2", "M10 2h4")
    val Chevron = icon("chevron", "M9 6l6 6-6 6")
    val Draw = icon("draw", "M6 5h9v14H6z", "M15 8l4 1-3 11-2-.5")
    val Pass = icon("pass", "M5 12h12", "M13 7l5 5-5 5")
    val Flag = icon("flag", "M6 21V4", "F:M6 4h11l-2.5 4L17 12H6z")
    val Mine = icon("mine", "F:" + circle(12f, 12f, 5.5f), "M12 2.5v3", "M12 18.5v3", "M2.5 12h3", "M18.5 12h3",
        "M5.3 5.3l2.1 2.1", "M16.6 16.6l2.1 2.1", "M5.3 18.7l2.1-2.1", "M16.6 7.4l2.1-2.1")
    val Minus = icon("minus", "M5 12h14")
    val Fit = icon("fit", "M4 9V4h5", "M20 9V4h-5", "M4 15v5h5", "M20 15v5h-5")
    val School = icon("school", "M2 9l10-5 10 5-10 5z", "M6 11.2v4.6c3 2.6 9 2.6 12 0v-4.6", "M22 9v6")
    val Person = icon("person", circle(12f, 8f, 4f), "M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7")
    val Settings = icon("settings", "M4 7h9", "M19 7h1", circle(16f, 7f, 2.5f), "M4 17h3", "M13 17h7", circle(10f, 17f, 2.5f))
    val Trophy = icon("trophy", "M8 4h8v5a4 4 0 0 1-8 0z", "M8 6H5a3 3 0 0 0 3 4", "M16 6h3a3 3 0 0 1-3 4", "M12 13v4", "M8 20h8", "M10 17h4")
    val Lock = icon("lock", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Play = icon("play", "F:M8 5l11 7-11 7z")
    val Fill = icon("fill", "M4 4h16v16H4z", "F:M7 7h10v10H7z")
    val Grid = icon("grid", "M4 4h16v16H4z", "M4 9.3h16", "M4 14.7h16", "M9.3 4v16", "M14.7 4v16")
    val Crossword = icon("crossword", "M3 9h18v6H3z", "M9 3h6v18H9z", "M9 9v6", "M15 9v6")
    val Search = icon("search", circle(10.5f, 10.5f, 6f), "M15 15l5.5 5.5")
    val Gallows = icon("gallows", "M4 21h9", "M7 21V3h9v3", circle(16f, 8.5f, 2.2f), "M16 11v5", "M16 16l-2 3", "M16 16l2 3", "M13.8 13h4.4")
}
