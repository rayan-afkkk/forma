package app.forma.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forma.presentation.progress.ExerciseProgressUi
import app.forma.presentation.progress.HistoryRowUi
import app.forma.presentation.progress.ProgressStateHolder
import app.forma.presentation.progress.ProgressUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.components.Badge
import app.forma.ui.components.EmptyState
import app.forma.ui.components.FormaCard
import app.forma.ui.components.LineChart
import app.forma.ui.components.LoadingState
import app.forma.ui.components.PageHeader
import app.forma.ui.components.ProBadge
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.StatTile
import app.forma.ui.components.WeeklyBars
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout

@Composable
fun ProgressRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("progress") { ProgressStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    ProgressScreen(state, holder)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(state: ProgressUiState, holder: ProgressStateHolder) {
    if (state.loading) {
        LoadingState()
        return
    }
    val pad = Layout.screenPadding
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("Progress") }
        if (state.empty) {
            item {
                EmptyState(
                    Illustration.SPROUT,
                    title = "Your progress starts here.",
                    body = "Complete your first session to begin your training history.",
                    actionLabel = "View today's workout",
                    onAction = holder::openToday,
                )
            }
            return@LazyColumn
        }
        item { StatRow(state.stats.map { it.label to it.value }) }
        item {
            FormaCard {
                Text("Weekly consistency", style = Forma.type.cardTitle, color = Forma.colors.text)
                Spacer(Modifier.height(4.dp))
                Text("Sessions per week, last 8 weeks", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                WeeklyBars(state.weeks)
                Spacer(Modifier.height(8.dp))
                Text(state.weeksNote, style = Forma.type.caption, color = Forma.colors.textMuted)
            }
        }
        if (state.milestones.isNotEmpty()) {
            item { SectionLabel("Milestones", Modifier.padding(start = 4.dp, top = 12.dp)) }
            item {
                FormaCard(padding = 16.dp) {
                    state.milestones.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(FormaIcons.Flag, contentDescription = null, tint = Forma.colors.accentText, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.foundation.layout.Column {
                                Text(m.title, style = Forma.type.bodyStrong, color = Forma.colors.text)
                                Text(m.detail, style = Forma.type.supporting, color = Forma.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
        if (state.exercises.isNotEmpty()) {
            item {
                Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Exercise performance", Modifier.weight(1f))
                    if (state.trendsLocked) ProBadge()
                }
            }
            items(state.exercises, key = { "ex-${it.exerciseId}" }) { ExerciseCard(it, state.trendsLocked) }
            if (state.trendsLocked) {
                item {
                    FormaCard(color = Forma.colors.surfaceSecondary) {
                        Text("Charts for each exercise are part of Pro", style = Forma.type.bodyStrong, color = Forma.colors.text)
                        Text("Your latest and best numbers above are always free, as is your full history.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("See Pro", holder::unlock, icon = FormaIcons.Sparkle)
                    }
                }
            }
        }
        item { SectionLabel("History", Modifier.padding(start = 4.dp, top = 12.dp)) }
        items(state.history, key = { "h-${it.sessionId}" }) { HistoryRow(it) { holder.openSession(it.sessionId) } }
    }
}

/** Stat tiles side by side, or stacked when the layout is compact (narrow screen or large text). */
@Composable
fun StatRow(stats: List<Pair<String, String>>) {
    if (Layout.compact) {
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEach { (label, value) -> StatTile(label, value, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            stats.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun ExerciseCard(ex: ExerciseProgressUi, locked: Boolean) {
    FormaCard(padding = 16.dp) {
        Text(ex.name, style = Forma.type.cardTitleSmall, color = Forma.colors.text)
        Text("${ex.metric} · ${ex.sessions} ${if (ex.sessions == 1) "session" else "sessions"}", style = Forma.type.caption, color = Forma.colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Text("Latest: ${ex.latest}", style = Forma.type.supporting, color = Forma.colors.textSecondary)
        Text("Best: ${ex.best}", style = Forma.type.supporting, color = Forma.colors.textSecondary)
        if (!locked && ex.points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            LineChart(ex.points, ex.unit)
            if (ex.converted) {
                Text("Some sets were logged in another unit and are shown converted.", style = Forma.type.caption, color = Forma.colors.textMuted)
            }
        }
    }
}

@Composable
private fun HistoryRow(row: HistoryRowUi, onClick: () -> Unit) {
    FormaCard(onClick = onClick, onClickLabel = "Open session", padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(row.title, style = Forma.type.bodyStrong, color = Forma.colors.text)
                Text("${row.date} · ${row.detail}", style = Forma.type.supporting, color = Forma.colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Badge(row.status, icon = if (row.partial) FormaIcons.Clock else FormaIcons.Check)
        }
    }
}
