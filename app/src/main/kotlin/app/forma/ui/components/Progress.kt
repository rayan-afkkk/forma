package app.forma.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.forma.core.engine.DayState
import app.forma.presentation.progress.ChartPoint
import app.forma.presentation.progress.WeekBarUi
import app.forma.presentation.today.WeekUi
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Shapes

/** Thin rounded progress bar, announced with its value. */
@Composable
fun ProgressBar(fraction: Float, description: String, modifier: Modifier = Modifier, color: Color = Forma.colors.text, track: Color = Forma.colors.border) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(Shapes.pill)
            .background(track)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                stateDescription = description
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(Shapes.pill).background(color))
    }
}

/** Circular timer ring. Purely visual; the time is shown as text next to it. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 220.dp, stroke: Dp = 8.dp, color: Color = Forma.colors.text, track: Color = Forma.colors.border) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val w = stroke.toPx()
        val inset = w / 2
        drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/**
 * Week at a glance. Done days are filled with a check; partial days are half filled; planned days
 * have a ring; planned days that passed without a session have a dashed ring (neutral, not red).
 * A text summary is always shown underneath.
 */
@Composable
fun WeekStrip(week: WeekUi, modifier: Modifier = Modifier) {
    val colors = Forma.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.days.forEach { day ->
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = day.description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.letter,
                        style = Forma.type.labelSmall.copy(fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (day.isToday) colors.text else colors.textMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    DayDot(day.state, day.dayNumber)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (day.isToday) colors.accent else Color.Transparent))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(week.summary, style = Forma.type.caption, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun DayDot(state: DayState, dayNumber: Int) {
    val colors = Forma.colors
    val size = 36.dp
    when (state) {
        DayState.DONE -> Box(Modifier.size(size).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(FormaIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        }
        DayState.PARTIAL -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(colors.primary, 90f, 180f, true)
                drawCircle(colors.primary, style = Stroke(1.5.dp.toPx()), radius = this.size.minDimension / 2 - 1.dp.toPx())
            }
        }
        DayState.PLANNED -> Box(Modifier.size(size).border(1.5.dp, colors.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
            Text(dayNumber.toString(), style = Forma.type.labelSmall, color = colors.text)
        }
        DayState.NOT_DONE -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    colors.textMuted,
                    radius = this.size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            Text(dayNumber.toString(), style = Forma.type.labelSmall, color = colors.textMuted)
        }
        DayState.REST -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(dayNumber.toString(), style = Forma.type.labelSmall, color = colors.textMuted)
        }
    }
}

/**
 * Weekly consistency bars: sessions done (filled) against sessions planned (outline), with a
 * labelled axis. Each bar is announced with its numbers.
 */
@Composable
fun WeeklyBars(weeks: List<WeekBarUi>, modifier: Modifier = Modifier) {
    val colors = Forma.colors
    val max = (weeks.maxOfOrNull { maxOf(it.completed, it.planned) } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(140.dp)) {
            Column(Modifier.fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(max.toString(), style = Forma.type.caption, color = colors.textMuted)
                Text("0", style = Forma.type.caption, color = colors.textMuted)
            }
            Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                weeks.forEach { week ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp).semantics(mergeDescendants = true) { contentDescription = week.description },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val barWidth = size.width.coerceAtMost(28.dp.toPx())
                            val left = (size.width - barWidth) / 2
                            val plannedH = size.height * week.planned / max
                            val doneH = size.height * week.completed / max
                            val radius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                            if (week.planned > 0) {
                                drawRoundRect(colors.borderStrong, topLeft = Offset(left, size.height - plannedH), size = Size(barWidth, plannedH), cornerRadius = radius, style = Stroke(1.dp.toPx()))
                            }
                            if (week.completed > 0) {
                                drawRoundRect(if (week.isCurrent) colors.accent else colors.text, topLeft = Offset(left, size.height - doneH), size = Size(barWidth, doneH), cornerRadius = radius)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            weeks.forEachIndexed { index, week ->
                Text(
                    if (index % 2 == 1 || index == weeks.lastIndex) week.label else "",
                    style = Forma.type.caption,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
            Box(Modifier.size(12.dp).clip(Shapes.small).background(colors.text))
            Spacer(Modifier.width(6.dp))
            Text("Done", style = Forma.type.caption, color = colors.textSecondary)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(12.dp).border(1.dp, colors.borderStrong, Shapes.small))
            Spacer(Modifier.width(6.dp))
            Text("Planned", style = Forma.type.caption, color = colors.textSecondary)
        }
    }
}

/** A simple labelled line chart for one metric with one unit. */
@Composable
fun LineChart(points: List<ChartPoint>, unit: String, modifier: Modifier = Modifier) {
    val colors = Forma.colors
    if (points.isEmpty()) return
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val range = (max - min).takeIf { it > 0f } ?: 1f
    val description = "Chart in $unit: " + points.joinToString("; ") { "${it.label}: ${trim(it.value)}" }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(110.dp)) {
            Column(Modifier.fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(trim(max), style = Forma.type.caption, color = colors.textMuted)
                Text(trim(min), style = Forma.type.caption, color = colors.textMuted)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                val step = if (points.size == 1) 0f else size.width / (points.size - 1)
                fun pos(i: Int) = Offset(if (points.size == 1) size.width / 2 else step * i, size.height - (points[i].value - min) / range * size.height)
                drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                for (i in 0 until points.size - 1) drawLine(colors.text, pos(i), pos(i + 1), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                for (i in points.indices) drawCircle(colors.text, radius = 3.5.dp.toPx(), center = pos(i))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = Forma.type.caption, color = colors.textMuted)
            Text(unit, style = Forma.type.caption, color = colors.textMuted)
            Text(points.last().label, style = Forma.type.caption, color = colors.textMuted)
        }
    }
}

private fun trim(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, value)
