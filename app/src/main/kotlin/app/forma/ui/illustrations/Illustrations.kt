package app.forma.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.forma.core.model.MovementPattern
import app.forma.ui.theme.Forma

/*
 * Original line illustrations. Drawn in a 160 × 120 design space and scaled to fit, using the
 * theme's text colour for lines and one pastel fill as an accent. All are decorative and hidden
 * from screen readers; the surrounding text carries the meaning.
 */

enum class Illustration { WELCOME, SPROUT, REST, COMPLETE, SEARCH, EQUIPMENT, SAFETY }

@Composable
fun FormaIllustration(
    kind: Illustration,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    accent: Color = Forma.colors.pastels.peach.container,
) {
    val line = Forma.colors.text
    val secondary = Forma.colors.textMuted
    Canvas(modifier.size(width, width * 0.75f).clearAndSetSemantics { }) {
        fit(160f, 120f) {
            val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val thin = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                Illustration.WELCOME -> welcome(line, secondary, accent, stroke, thin)
                Illustration.SPROUT -> sprout(line, secondary, accent, stroke, thin)
                Illustration.REST -> rest(line, secondary, accent, stroke, thin)
                Illustration.COMPLETE -> complete(line, secondary, accent, stroke, thin)
                Illustration.SEARCH -> search(line, secondary, accent, stroke, thin)
                Illustration.EQUIPMENT -> equipment(line, secondary, accent, stroke, thin)
                Illustration.SAFETY -> safety(line, secondary, accent, stroke, thin)
            }
        }
    }
}

private inline fun DrawScope.fit(w: Float, h: Float, block: DrawScope.() -> Unit) {
    val s = minOf(size.width / w, size.height / h)
    translate((size.width - w * s) / 2f, (size.height - h * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { block() }
    }
}

/**
 * Minimal path builder that tracks the current point so quadratic curves can be expressed as
 * cubics (the only curve API that is current in every Compose version this code compiles against).
 */
private class Pen {
    val path = Path()
    private var x = 0f
    private var y = 0f

    fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y); this.x = x; this.y = y
    }

    fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y); this.x = x; this.y = y
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.cubicTo(x1, y1, x2, y2, x3, y3); x = x3; y = y3
    }

    fun quadraticBezierTo(cx: Float, cy: Float, x3: Float, y3: Float) {
        val c1x = x + 2f / 3f * (cx - x)
        val c1y = y + 2f / 3f * (cy - y)
        val c2x = x3 + 2f / 3f * (cx - x3)
        val c2y = y3 + 2f / 3f * (cy - y3)
        cubicTo(c1x, c1y, c2x, c2y, x3, y3)
    }

    fun close() = path.close()
}

private fun path(block: Pen.() -> Unit): Path = Pen().apply(block).path

private fun DrawScope.ground(color: Color, stroke: Stroke, y: Float = 104f) {
    drawLine(color, Offset(18f, y), Offset(142f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.welcome(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    // A window with the sun, a rolled-out mat and a dumbbell on the floor.
    drawCircle(accent, radius = 13f, center = Offset(58f, 34f))
    drawRoundRect(line, topLeft = Offset(34f, 14f), size = Size(56f, 52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f), style = stroke)
    drawLine(line, Offset(62f, 14f), Offset(62f, 66f), strokeWidth = thin.width)
    drawLine(line, Offset(34f, 40f), Offset(90f, 40f), strokeWidth = thin.width)
    // Mat
    drawPath(path { moveTo(40f, 98f); lineTo(118f, 98f); quadraticBezierTo(126f, 98f, 126f, 92f); quadraticBezierTo(126f, 86f, 118f, 86f); quadraticBezierTo(112f, 86f, 112f, 92f) }, line, style = stroke)
    drawLine(line, Offset(40f, 98f), Offset(40f, 104f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Dumbbell
    drawLine(line, Offset(104f, 72f), Offset(124f, 72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawRoundRect(line, topLeft = Offset(98f, 64f), size = Size(7f, 16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
    drawRoundRect(line, topLeft = Offset(123f, 64f), size = Size(7f, 16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
    // Plant
    drawPath(path { moveTo(18f, 104f); lineTo(20f, 88f); lineTo(30f, 88f); lineTo(32f, 104f) }, line, style = stroke)
    drawPath(path { moveTo(25f, 88f); quadraticBezierTo(22f, 74f, 14f, 70f); moveTo(25f, 86f); quadraticBezierTo(28f, 72f, 36f, 66f) }, secondary, style = thin)
    ground(line, stroke)
}

private fun DrawScope.sprout(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    drawCircle(accent, radius = 14f, center = Offset(116f, 30f))
    // Pot
    drawPath(path { moveTo(62f, 78f); lineTo(98f, 78f); lineTo(93f, 104f); lineTo(67f, 104f); close() }, line, style = stroke)
    drawLine(line, Offset(58f, 78f), Offset(102f, 78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    // Stem and leaves
    drawPath(path { moveTo(80f, 78f); quadraticBezierTo(79f, 60f, 80f, 44f) }, line, style = stroke)
    drawPath(path { moveTo(80f, 58f); quadraticBezierTo(66f, 58f, 60f, 46f); quadraticBezierTo(74f, 44f, 80f, 58f) }, line, style = stroke)
    drawPath(path { moveTo(80f, 50f); quadraticBezierTo(92f, 46f, 98f, 34f); quadraticBezierTo(84f, 34f, 80f, 50f) }, line, style = stroke)
    // Small marks
    drawLine(secondary, Offset(36f, 60f), Offset(36f, 66f), strokeWidth = thin.width, cap = StrokeCap.Round)
    drawLine(secondary, Offset(33f, 63f), Offset(39f, 63f), strokeWidth = thin.width, cap = StrokeCap.Round)
    drawLine(secondary, Offset(126f, 66f), Offset(126f, 72f), strokeWidth = thin.width, cap = StrokeCap.Round)
    drawLine(secondary, Offset(123f, 69f), Offset(129f, 69f), strokeWidth = thin.width, cap = StrokeCap.Round)
    ground(line, stroke)
}

private fun DrawScope.rest(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    val moon = path {
        moveTo(98f, 22f)
        cubicTo(82f, 24f, 72f, 38f, 74f, 54f)
        cubicTo(76f, 70f, 92f, 80f, 108f, 76f)
        cubicTo(98f, 72f, 90f, 62f, 90f, 50f)
        cubicTo(90f, 38f, 94f, 28f, 98f, 22f)
        close()
    }
    drawPath(moon, accent)
    drawPath(moon, line, style = stroke)
    for ((x, y) in listOf(46f to 30f, 128f to 44f, 56f to 62f)) {
        drawLine(secondary, Offset(x, y - 5f), Offset(x, y + 5f), strokeWidth = thin.width, cap = StrokeCap.Round)
        drawLine(secondary, Offset(x - 5f, y), Offset(x + 5f, y), strokeWidth = thin.width, cap = StrokeCap.Round)
    }
    // A cup on a small table.
    drawPath(path { moveTo(30f, 86f); lineTo(32f, 100f); quadraticBezierTo(33f, 104f, 37f, 104f); lineTo(47f, 104f); quadraticBezierTo(51f, 104f, 52f, 100f); lineTo(54f, 86f); close() }, line, style = stroke)
    drawPath(path { moveTo(54f, 90f); quadraticBezierTo(62f, 90f, 60f, 96f); quadraticBezierTo(58f, 100f, 53f, 99f) }, line, style = thin)
    drawPath(path { moveTo(38f, 80f); quadraticBezierTo(36f, 76f, 39f, 72f); moveTo(46f, 80f); quadraticBezierTo(44f, 76f, 47f, 72f) }, secondary, style = thin)
    ground(line, stroke)
}

private fun DrawScope.complete(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    drawCircle(accent, radius = 30f, center = Offset(80f, 58f))
    drawCircle(line, radius = 30f, center = Offset(80f, 58f), style = stroke)
    drawPath(path { moveTo(66f, 58f); lineTo(76f, 68f); lineTo(95f, 48f) }, line, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for ((a, b) in listOf(Offset(40f, 30f) to Offset(46f, 36f), Offset(120f, 30f) to Offset(114f, 36f), Offset(36f, 74f) to Offset(44f, 74f), Offset(124f, 74f) to Offset(116f, 74f), Offset(80f, 14f) to Offset(80f, 22f))) {
        drawLine(secondary, a, b, strokeWidth = thin.width, cap = StrokeCap.Round)
    }
    ground(line, stroke, y = 104f)
}

private fun DrawScope.search(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    drawCircle(accent, radius = 22f, center = Offset(72f, 52f))
    drawCircle(line, radius = 22f, center = Offset(72f, 52f), style = stroke)
    drawLine(line, Offset(88f, 68f), Offset(108f, 88f), strokeWidth = 3.2f, cap = StrokeCap.Round)
    drawLine(secondary, Offset(62f, 46f), Offset(70f, 40f), strokeWidth = thin.width, cap = StrokeCap.Round)
    ground(line, stroke)
}

private fun DrawScope.equipment(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    // A bench with a pair of dumbbells.
    drawRoundRect(accent, topLeft = Offset(30f, 66f), size = Size(100f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f))
    drawRoundRect(line, topLeft = Offset(30f, 66f), size = Size(100f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f), style = stroke)
    drawLine(line, Offset(44f, 76f), Offset(44f, 104f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(line, Offset(116f, 76f), Offset(116f, 104f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    for (y in listOf(40f, 54f)) {
        drawLine(line, Offset(64f, y), Offset(96f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawRoundRect(line, topLeft = Offset(56f, y - 7f), size = Size(8f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f), style = thin)
        drawRoundRect(line, topLeft = Offset(96f, y - 7f), size = Size(8f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f), style = thin)
    }
    ground(line, stroke)
}

private fun DrawScope.safety(line: Color, secondary: Color, accent: Color, stroke: Stroke, thin: Stroke) {
    val heart = path {
        moveTo(80f, 92f)
        cubicTo(80f, 92f, 46f, 72f, 46f, 48f)
        cubicTo(46f, 37f, 54f, 29f, 64f, 29f)
        cubicTo(71f, 29f, 77f, 33f, 80f, 39f)
        cubicTo(83f, 33f, 89f, 29f, 96f, 29f)
        cubicTo(106f, 29f, 114f, 37f, 114f, 48f)
        cubicTo(114f, 72f, 80f, 92f, 80f, 92f)
        close()
    }
    drawPath(heart, accent)
    drawPath(heart, line, style = stroke)
    drawPath(path { moveTo(58f, 56f); lineTo(70f, 56f); lineTo(75f, 46f); lineTo(83f, 66f); lineTo(88f, 56f); lineTo(102f, 56f) }, line, style = thin)
    ground(line, stroke)
}

// ---------------------------------------------------------------------------------------- demo figures

/**
 * A simple line figure in a pose that matches the movement pattern. Used by the demonstration
 * placeholder until licensed demonstration media is available.
 */
@Composable
fun PoseFigure(pattern: MovementPattern, modifier: Modifier = Modifier, color: Color = Forma.colors.text, accent: Color = Forma.colors.accent) {
    Canvas(modifier.clearAndSetSemantics { }) {
        fit(160f, 120f) {
            val s = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun limb(vararg xy: Float) = drawPath(path { moveTo(xy[0], xy[1]); var i = 2; while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 } }, color, style = s)
            fun head(x: Float, y: Float) = drawCircle(color, radius = 8f, center = Offset(x, y), style = s)
            drawLine(color.copy(alpha = 0.35f), Offset(14f, 108f), Offset(146f, 108f), strokeWidth = 2f, cap = StrokeCap.Round)
            when (pattern) {
                MovementPattern.SQUAT -> {
                    head(84f, 30f)
                    limb(82f, 40f, 72f, 66f)
                    limb(72f, 66f, 94f, 76f, 88f, 106f)
                    limb(80f, 48f, 102f, 54f)
                    drawCircle(accent, radius = 5f, center = Offset(104f, 54f))
                }
                MovementPattern.LUNGE -> {
                    head(80f, 22f)
                    limb(80f, 32f, 80f, 62f)
                    limb(80f, 62f, 100f, 80f, 100f, 106f)
                    limb(80f, 62f, 64f, 92f, 46f, 104f)
                    limb(80f, 40f, 70f, 58f)
                }
                MovementPattern.HINGE -> {
                    head(52f, 50f)
                    limb(60f, 54f, 92f, 62f)
                    limb(92f, 62f, 96f, 84f, 94f, 106f)
                    limb(70f, 57f, 72f, 84f)
                    drawCircle(accent, radius = 5f, center = Offset(72f, 88f))
                }
                MovementPattern.PUSH_HORIZONTAL, MovementPattern.CORE, MovementPattern.CONDITIONING -> {
                    head(40f, 66f)
                    limb(48f, 70f, 124f, 96f)
                    limb(124f, 96f, 136f, 106f)
                    limb(56f, 73f, 56f, 106f)
                }
                MovementPattern.PUSH_VERTICAL -> {
                    head(80f, 40f)
                    limb(80f, 50f, 80f, 80f)
                    limb(80f, 80f, 70f, 106f)
                    limb(80f, 80f, 90f, 106f)
                    limb(80f, 56f, 64f, 44f, 66f, 20f)
                    limb(80f, 56f, 96f, 44f, 94f, 20f)
                    drawCircle(accent, radius = 5f, center = Offset(66f, 18f))
                    drawCircle(accent, radius = 5f, center = Offset(94f, 18f))
                }
                MovementPattern.PULL -> {
                    head(54f, 46f)
                    limb(62f, 50f, 98f, 62f)
                    limb(98f, 62f, 104f, 84f, 100f, 106f)
                    limb(72f, 54f, 84f, 70f, 76f, 80f)
                    drawCircle(accent, radius = 5f, center = Offset(76f, 82f))
                }
                MovementPattern.MOBILITY, MovementPattern.STRETCH -> {
                    head(46f, 84f)
                    limb(54f, 88f, 92f, 100f)
                    limb(92f, 100f, 108f, 76f, 122f, 102f)
                    limb(62f, 92f, 70f, 70f)
                }
                else -> {
                    head(80f, 22f)
                    limb(80f, 32f, 80f, 66f)
                    limb(80f, 66f, 70f, 106f)
                    limb(80f, 66f, 90f, 106f)
                    limb(80f, 40f, 62f, 60f)
                    limb(80f, 40f, 98f, 60f)
                }
            }
        }
    }
}
