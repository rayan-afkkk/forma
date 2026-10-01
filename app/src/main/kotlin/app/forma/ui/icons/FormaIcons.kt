package app.forma.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * Original outline icons for Forma, drawn on a 24-unit grid with a 1.6 stroke. Tinted at use, so
 * the stroke colour here is irrelevant.
 */

private fun outline(name: String, stroke: Float = 1.6f, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        .build()

private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(fill = SolidColor(Color.Black), pathBuilder = block)
        .build()

internal fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

internal fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

internal fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
    moveTo(left + r, top)
    lineTo(right - r, top)
    arcTo(r, r, 0f, false, true, right, top + r)
    lineTo(right, bottom - r)
    arcTo(r, r, 0f, false, true, right - r, bottom)
    lineTo(left + r, bottom)
    arcTo(r, r, 0f, false, true, left, bottom - r)
    lineTo(left, top + r)
    arcTo(r, r, 0f, false, true, left + r, top)
    close()
}

object FormaIcons {
    val Today = outline("today") {
        circle(12f, 12f, 3.8f)
        val rays = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
        for (deg in rays) {
            val rad = Math.toRadians(deg.toDouble())
            val c = kotlin.math.cos(rad).toFloat()
            val s = kotlin.math.sin(rad).toFloat()
            line(12f + c * 6.6f, 12f + s * 6.6f, 12f + c * 8.6f, 12f + s * 8.6f)
        }
    }
    val Explore = outline("explore") {
        circle(12f, 12f, 8.6f)
        moveTo(15.2f, 8.8f); lineTo(13.2f, 13.2f); lineTo(8.8f, 15.2f); lineTo(10.8f, 10.8f); close()
    }
    val Progress = outline("progress") {
        line(6f, 19f, 6f, 13.5f)
        line(12f, 19f, 12f, 9f)
        line(18f, 19f, 18f, 5f)
    }
    val You = outline("you") {
        circle(12f, 8.6f, 3.6f)
        moveTo(5f, 19.8f); curveTo(5.8f, 15.8f, 8.6f, 14f, 12f, 14f); curveTo(15.4f, 14f, 18.2f, 15.8f, 19f, 19.8f)
    }
    val ChevronRight = outline("chevron_right") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
    val Back = outline("back") { moveTo(14.5f, 6f); lineTo(8.5f, 12f); lineTo(14.5f, 18f) }
    val Close = outline("close") { line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f) }
    val Plus = outline("plus", 1.8f) { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
    val Minus = outline("minus", 1.8f) { line(5f, 12f, 19f, 12f) }
    val Check = outline("check", 1.8f) { moveTo(5f, 12.5f); lineTo(9.8f, 17f); lineTo(19f, 7.5f) }
    val Play = outline("play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }
    val Pause = outline("pause", 1.8f) { line(9f, 6f, 9f, 18f); line(15f, 6f, 15f, 18f) }
    val Search = outline("search") { circle(11f, 11f, 6.2f); line(15.6f, 15.6f, 20f, 20f) }
    val Heart = outline("heart") {
        moveTo(12f, 19.5f)
        curveTo(12f, 19.5f, 4f, 15f, 4f, 9.4f)
        curveTo(4f, 6.9f, 6f, 5f, 8.4f, 5f)
        curveTo(10f, 5f, 11.3f, 5.9f, 12f, 7.2f)
        curveTo(12.7f, 5.9f, 14f, 5f, 15.6f, 5f)
        curveTo(18f, 5f, 20f, 6.9f, 20f, 9.4f)
        curveTo(20f, 15f, 12f, 19.5f, 12f, 19.5f)
        close()
    }
    val HeartFilled = filled("heart_filled") {
        moveTo(12f, 20.3f)
        curveTo(12f, 20.3f, 3.2f, 15.4f, 3.2f, 9.4f)
        curveTo(3.2f, 6.4f, 5.6f, 4.2f, 8.4f, 4.2f)
        curveTo(9.9f, 4.2f, 11.2f, 4.9f, 12f, 6f)
        curveTo(12.8f, 4.9f, 14.1f, 4.2f, 15.6f, 4.2f)
        curveTo(18.4f, 4.2f, 20.8f, 6.4f, 20.8f, 9.4f)
        curveTo(20.8f, 15.4f, 12f, 20.3f, 12f, 20.3f)
        close()
    }
    val Swap = outline("swap") {
        line(6f, 8f, 18f, 8f); moveTo(14.5f, 4.5f); lineTo(18f, 8f); lineTo(14.5f, 11.5f)
        line(18f, 16f, 6f, 16f); moveTo(9.5f, 12.5f); lineTo(6f, 16f); lineTo(9.5f, 19.5f)
    }
    val Skip = outline("skip") { moveTo(6f, 6.5f); lineTo(14f, 12f); lineTo(6f, 17.5f); close(); line(18f, 6f, 18f, 18f) }
    val Timer = outline("timer") { circle(12f, 13f, 7.2f); line(12f, 13f, 12f, 9.4f); line(10f, 3.6f, 14f, 3.6f); line(18.4f, 6.6f, 17.2f, 7.8f) }
    val Dumbbell = outline("dumbbell", 1.7f) {
        line(8f, 12f, 16f, 12f)
        roundRect(5.2f, 7f, 8f, 17f, 1.2f)
        roundRect(16f, 7f, 18.8f, 17f, 1.2f)
        line(3.2f, 10f, 3.2f, 14f)
        line(20.8f, 10f, 20.8f, 14f)
    }
    val Bodyweight = outline("bodyweight") {
        circle(12f, 4.8f, 2f)
        line(12f, 7.6f, 12f, 14f)
        moveTo(6.5f, 10.2f); lineTo(12f, 9f); lineTo(17.5f, 10.2f)
        moveTo(8.5f, 20f); lineTo(12f, 14f); lineTo(15.5f, 20f)
    }
    val Clock = outline("clock") { circle(12f, 12f, 8.6f); moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15f, 14f) }
    val Info = outline("info") { circle(12f, 12f, 8.6f); line(12f, 11f, 12f, 16.4f); line(12f, 7.8f, 12f, 7.85f) }
    val Bell = outline("bell") {
        moveTo(6f, 16.5f); lineTo(6f, 11f); curveTo(6f, 7.7f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.7f, 18f, 11f)
        lineTo(18f, 16.5f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(10f, 20.6f); curveTo(10.5f, 21.2f, 11.2f, 21.5f, 12f, 21.5f); curveTo(12.8f, 21.5f, 13.5f, 21.2f, 14f, 20.6f)
    }
    val Moon = outline("moon") {
        moveTo(19.2f, 14.6f)
        curveTo(18.1f, 15.2f, 16.8f, 15.6f, 15.4f, 15.6f)
        curveTo(11.3f, 15.6f, 8.1f, 12.3f, 8.1f, 8.3f)
        curveTo(8.1f, 6.9f, 8.5f, 5.6f, 9.1f, 4.5f)
        curveTo(6f, 5.6f, 3.8f, 8.6f, 3.8f, 12f)
        curveTo(3.8f, 16.5f, 7.5f, 20.2f, 12f, 20.2f)
        curveTo(15.4f, 20.2f, 18.3f, 18f, 19.2f, 14.6f)
        close()
    }
    val Sound = outline("sound") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        moveTo(15.4f, 9f); curveTo(16.6f, 10.4f, 16.6f, 13.6f, 15.4f, 15f)
        moveTo(18f, 6.5f); curveTo(20.4f, 8.8f, 20.4f, 15.2f, 18f, 17.5f)
    }
    val Quiet = outline("quiet") {
        moveTo(4f, 9.5f); lineTo(7.5f, 9.5f); lineTo(12f, 5.5f); lineTo(12f, 18.5f); lineTo(7.5f, 14.5f); lineTo(4f, 14.5f); close()
        line(15.5f, 9.5f, 20f, 14f); line(20f, 9.5f, 15.5f, 14f)
    }
    val Vibrate = outline("vibrate") { roundRect(8f, 4f, 16f, 20f, 2f); line(4.8f, 9f, 4.8f, 15f); line(19.2f, 9f, 19.2f, 15f) }
    val Export = outline("export") { line(12f, 4f, 12f, 15f); moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f); line(5f, 19.5f, 19f, 19.5f) }
    val Import = outline("import") { line(12f, 15f, 12f, 4f); moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f); line(5f, 19.5f, 19f, 19.5f) }
    val Shield = outline("shield") {
        moveTo(12f, 3.5f); lineTo(19f, 6.4f); lineTo(19f, 11.5f); curveTo(19f, 15.8f, 16f, 19.1f, 12f, 20.5f)
        curveTo(8f, 19.1f, 5f, 15.8f, 5f, 11.5f); lineTo(5f, 6.4f); close()
    }
    val Help = outline("help") {
        circle(12f, 12f, 8.6f)
        moveTo(9.6f, 9.6f); curveTo(9.6f, 8.2f, 10.7f, 7.2f, 12f, 7.2f); curveTo(13.3f, 7.2f, 14.4f, 8.2f, 14.4f, 9.6f)
        curveTo(14.4f, 11.5f, 12f, 11.6f, 12f, 13.6f)
        line(12f, 16.6f, 12f, 16.65f)
    }
    val Trash = outline("trash") {
        line(5f, 7f, 19f, 7f); line(10f, 4f, 14f, 4f)
        moveTo(7f, 7f); lineTo(8f, 19.5f); lineTo(16f, 19.5f); lineTo(17f, 7f)
        line(10.5f, 10.5f, 10.5f, 16f); line(13.5f, 10.5f, 13.5f, 16f)
    }
    val Lock = outline("lock") {
        roundRect(6f, 10.5f, 18f, 20f, 2f)
        moveTo(8.6f, 10.5f); lineTo(8.6f, 8f); curveTo(8.6f, 6.1f, 10.1f, 4.6f, 12f, 4.6f); curveTo(13.9f, 4.6f, 15.4f, 6.1f, 15.4f, 8f); lineTo(15.4f, 10.5f)
    }
    val Sparkle = outline("sparkle") {
        moveTo(12f, 3.5f); curveTo(12.6f, 8.5f, 15.5f, 11.4f, 20.5f, 12f); curveTo(15.5f, 12.6f, 12.6f, 15.5f, 12f, 20.5f)
        curveTo(11.4f, 15.5f, 8.5f, 12.6f, 3.5f, 12f); curveTo(8.5f, 11.4f, 11.4f, 8.5f, 12f, 3.5f); close()
    }
    val Edit = outline("edit") { moveTo(5f, 19f); lineTo(5.6f, 15.4f); lineTo(15.6f, 5.4f); lineTo(18.6f, 8.4f); lineTo(8.6f, 18.4f); close(); line(13.6f, 7.4f, 16.6f, 10.4f) }
    val Calendar = outline("calendar") { roundRect(4f, 5.5f, 20f, 20f, 2.5f); line(4f, 10f, 20f, 10f); line(8.5f, 3.5f, 8.5f, 7f); line(15.5f, 3.5f, 15.5f, 7f) }
    val Sliders = outline("sliders") {
        line(4.5f, 7.5f, 12.5f, 7.5f); line(17.5f, 7.5f, 19.5f, 7.5f); circle(15f, 7.5f, 2.2f)
        line(4.5f, 16.5f, 6.5f, 16.5f); line(11.5f, 16.5f, 19.5f, 16.5f); circle(9f, 16.5f, 2.2f)
    }
    val Undo = outline("undo") { moveTo(9f, 8f); lineTo(5f, 12f); lineTo(9f, 16f); moveTo(5f, 12f); lineTo(14.5f, 12f); curveTo(17f, 12f, 19f, 14f, 19f, 16.5f); lineTo(19f, 18f) }
    val More = filled("more") { circle(6f, 12f, 1.5f); circle(12f, 12f, 1.5f); circle(18f, 12f, 1.5f) }
    val Leaf = outline("leaf") { moveTo(5f, 19f); curveTo(5f, 11f, 10f, 5.5f, 19f, 5f); curveTo(18.5f, 14f, 13f, 19f, 5f, 19f); close(); line(5f, 19f, 13f, 11f) }
    val Easier = outline("easier") { moveTo(4f, 7f); lineTo(10f, 13f); lineTo(13.5f, 9.5f); lineTo(20f, 16f); moveTo(20f, 11f); lineTo(20f, 16f); lineTo(15f, 16f) }
    val External = outline("external") { moveTo(14f, 5f); lineTo(19f, 5f); lineTo(19f, 10f); line(19f, 5f, 11f, 13f); moveTo(18f, 14f); lineTo(18f, 18.5f); lineTo(5.5f, 18.5f); lineTo(5.5f, 6f); lineTo(10f, 6f) }
    val Flag = outline("flag") { line(6f, 20.5f, 6f, 4.5f); moveTo(6f, 5f); lineTo(17f, 5f); lineTo(15f, 8.5f); lineTo(17f, 12f); lineTo(6f, 12f) }
    val ArrowUp = outline("arrow_up", 1.8f) { line(12f, 18f, 12f, 6f); moveTo(7f, 11f); lineTo(12f, 6f); lineTo(17f, 11f) }
    val ArrowDown = outline("arrow_down", 1.8f) { line(12f, 6f, 12f, 18f); moveTo(7f, 13f); lineTo(12f, 18f); lineTo(17f, 13f) }
    val Equals = outline("equals", 1.8f) { line(6f, 10f, 18f, 10f); line(6f, 14f, 18f, 14f) }
    val Overview = outline("overview") {
        line(9.5f, 7f, 19f, 7f); line(9.5f, 12f, 19f, 12f); line(9.5f, 17f, 19f, 17f)
        line(5f, 7f, 5.05f, 7f); line(5f, 12f, 5.05f, 12f); line(5f, 17f, 5.05f, 17f)
    }
    val Bench = outline("bench") { line(3.5f, 11f, 20.5f, 11f); line(3.5f, 14f, 20.5f, 14f); line(6.5f, 14f, 6.5f, 19f); line(17.5f, 14f, 17.5f, 19f) }
    val Space = outline("space") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f); moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f); moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
    }
    val Target = outline("target") { circle(12f, 12f, 8.6f); circle(12f, 12f, 4.6f); line(12f, 12f, 12.05f, 12f) }
    val Weight = outline("weight") { roundRect(4.5f, 8f, 19.5f, 20f, 3f); circle(12f, 5.4f, 2f); line(10f, 14f, 14f, 14f) }
    val Note = outline("note") { roundRect(5f, 4f, 19f, 20f, 2.5f); line(8.5f, 9f, 15.5f, 9f); line(8.5f, 12.5f, 15.5f, 12.5f); line(8.5f, 16f, 12.5f, 16f) }
}
