package app.forma.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.forma.core.model.PlayerPhase
import app.forma.presentation.player.PlayerSheet
import app.forma.presentation.player.PlayerStateHolder
import app.forma.presentation.player.PlayerUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.DemonstrationArea
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaBottomSheet
import app.forma.ui.components.FormaCard
import app.forma.ui.components.LoadingState
import app.forma.ui.components.MissingState
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProgressBar
import app.forma.ui.components.ProgressRing
import app.forma.ui.components.RoundIconButton
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.Stepper
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.KeepScreenOn
import app.forma.ui.platform.PlatformBackHandler
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.preview.ReplaceSheet
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout
import app.forma.ui.theme.Shapes
import app.forma.ui.theme.maxScale

@Composable
fun PlayerRoute(sessionId: String) {
    val services = LocalAppServices.current
    val liveTimers = app.forma.ui.app.LocalLiveTimers.current
    val holder = rememberStateHolder("player:$sessionId") { PlayerStateHolder(it, services, sessionId, autoTick = liveTimers) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    PlayerScreen(state, holder)
}

@Composable
fun PlayerScreen(state: PlayerUiState, holder: PlayerStateHolder) {
    val navigator = LocalNavigator.current
    if (state.loading) {
        LoadingState()
        return
    }
    if (state.missing) {
        MissingState("This session has already finished or was removed.", navigator::back)
        return
    }
    // Keep the display on only while a workout is actively running, per the person's setting.
    KeepScreenOn(state.keepScreenOn && !state.paused && state.phase != PlayerPhase.FINISHED)
    PlatformBackHandler(enabled = true) { holder.onBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Top: menu, elapsed time, overview.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(FormaIcons.Close, "Workout menu: pause, save and exit, or finish", holder::openMenu)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.elapsed, style = Forma.type.numberMedium.maxScale(1.5f), color = Forma.colors.text)
                Text(if (state.paused) "Paused" else state.title, style = Forma.type.caption, color = Forma.colors.textMuted, maxLines = 1)
            }
            RoundIconButton(FormaIcons.Overview, "All exercises in this workout", holder::openOverview)
        }
        Column(Modifier.padding(horizontal = Layout.screenPadding)) {
            ProgressBar(state.progress, state.progressDescription)
            Spacer(Modifier.height(6.dp))
            Text(state.progressDescription, style = Forma.type.caption, color = Forma.colors.textMuted)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Layout.screenPadding)
                .padding(top = 16.dp, bottom = 16.dp),
        ) {
            if (state.paused) {
                FormaCard(color = Forma.colors.surfaceSecondary) {
                    Text("Paused", style = Forma.type.cardTitle, color = Forma.colors.text)
                    Text("Your timers are stopped. Take the time you need.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Resume", holder::resume, icon = FormaIcons.Play)
                }
                Spacer(Modifier.height(16.dp))
            }
            when (state.phase) {
                PlayerPhase.FINISHED -> FinishedContent(holder)
                PlayerPhase.REST -> RestContent(state, holder)
                PlayerPhase.WORK -> WorkContent(state, holder)
            }
        }

        // Bottom actions stay put so they are easy to reach while moving.
        if (state.phase == PlayerPhase.WORK) {
            Column(Modifier.padding(horizontal = Layout.screenPadding).padding(bottom = 8.dp)) {
                val timed = state.timed
                PrimaryButton(
                    text = when {
                        timed == null -> "Complete set"
                        timed.done -> "Complete set"
                        timed.running -> "Complete set early"
                        else -> "Mark set as done"
                    },
                    onClick = holder::completeSet,
                    icon = FormaIcons.Check,
                    enabled = !state.paused,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActionButton(FormaIcons.Swap, "Replace", Modifier.weight(1f), holder::openReplace)
                    ActionButton(FormaIcons.Easier, "Easier", Modifier.weight(1f), holder::openEasier)
                    ActionButton(FormaIcons.Skip, "Skip", Modifier.weight(1f), holder::openSkip)
                    if (state.paused) ActionButton(FormaIcons.Play, "Resume", Modifier.weight(1f), holder::resume)
                    else ActionButton(FormaIcons.Pause, "Pause", Modifier.weight(1f), holder::pause)
                }
            }
        }
    }

    Sheets(state, holder)
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(Shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Forma.colors.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Forma.type.labelSmall.maxScale(1.3f), color = Forma.colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun WorkContent(state: PlayerUiState, holder: PlayerStateHolder) {
    Text(state.section, style = Forma.type.eyebrow, color = Forma.colors.textMuted)
    Spacer(Modifier.height(4.dp))
    Text(state.exerciseName, style = Forma.type.exerciseName, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
    Text("${state.setLabel} · ${state.targetText}", style = Forma.type.body, color = Forma.colors.textSecondary)
    state.replacedFrom?.let {
        Spacer(Modifier.height(4.dp))
        Text("Replacing $it in this workout", style = Forma.type.caption, color = Forma.colors.textMuted)
    }
    // With large text or a small screen, logging controls come first so they need no scrolling.
    val demoFirst = !Layout.compact
    if (demoFirst) {
        Spacer(Modifier.height(14.dp))
        DemonstrationArea(state.pattern, state.exerciseName, compact = true)
    }
    Spacer(Modifier.height(18.dp))

    state.reps?.let { reps ->
        Stepper(
            value = reps.reps.toString(),
            label = if (reps.reps == 1) "rep" else "reps",
            onMinus = { holder.changeReps(-1) },
            onPlus = { holder.changeReps(1) },
            minusEnabled = reps.reps > 0,
            minusDescription = "One fewer rep",
            plusDescription = "One more rep",
        )
        Text(
            "Log what you actually did. You set the pace.",
            style = Forma.type.caption, color = Forma.colors.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        val loadText = reps.loadText
        if (loadText != null) {
            Spacer(Modifier.height(16.dp))
            FormaCard(padding = 12.dp) {
                Stepper(
                    value = loadText,
                    label = "weight",
                    onMinus = { holder.changeLoad(heavier = false) },
                    onPlus = { holder.changeLoad(heavier = true) },
                    minusEnabled = reps.canLighter,
                    plusEnabled = reps.canHeavier,
                    minusDescription = "Lighter weight",
                    plusDescription = "Heavier weight",
                    large = false,
                )
            }
        }
    }

    state.timed?.let { timed ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(timed.fraction, size = 200.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The ticking number is not a live region, so screen readers aren't interrupted every second.
                Text(
                    timed.remainingText,
                    style = Forma.type.numberHuge.maxScale(1.4f),
                    color = Forma.colors.text,
                    modifier = Modifier.semantics { contentDescription = "${timed.remainingText} remaining" },
                )
                if (timed.done) {
                    Text(
                        "Time's up",
                        style = Forma.type.supporting,
                        color = Forma.colors.textSecondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(if (timed.running) "remaining" else "ready", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                }
            }
        }
        timed.perSideNote?.let {
            Text(it, style = Forma.type.supporting, color = Forma.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (timed.running) {
                SecondaryButton("Pause timer", holder::pauseTimer, Modifier.weight(1f), icon = FormaIcons.Pause)
            } else {
                SecondaryButton(if (timed.fraction > 0f && !timed.done) "Continue timer" else "Start timer", holder::startTimer, Modifier.weight(1f), icon = FormaIcons.Play, enabled = !timed.done && !state.paused)
            }
            SecondaryButton("Reset", holder::resetTimer, Modifier.weight(1f), icon = FormaIcons.Undo)
        }
    }

    if (!demoFirst) {
        Spacer(Modifier.height(18.dp))
        DemonstrationArea(state.pattern, state.exerciseName, compact = true)
    }
    if (state.cues.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        SectionLabel("Technique")
        state.cues.forEach { cue ->
            Row(Modifier.padding(top = 8.dp)) {
                Box(Modifier.padding(top = 9.dp).size(5.dp).clip(CircleShape).background(Forma.colors.textSecondary))
                Spacer(Modifier.width(10.dp))
                Text(cue, style = Forma.type.body, color = Forma.colors.text)
            }
        }
    }
    state.next?.let { next ->
        Spacer(Modifier.height(18.dp))
        NextCard(next.label, next.name, next.detail)
    }
}

@Composable
private fun NextCard(label: String, name: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.small).border(1.dp, Forma.colors.border, Shapes.small).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Forma.type.eyebrow, color = Forma.colors.textMuted)
            Text(name, style = Forma.type.bodyStrong, color = Forma.colors.text)
            Text(detail, style = Forma.type.supporting, color = Forma.colors.textSecondary)
        }
    }
}

@Composable
private fun RestContent(state: PlayerUiState, holder: PlayerStateHolder) {
    Text("Rest", style = Forma.type.eyebrow, color = Forma.colors.textMuted)
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing(state.restFraction, size = 220.dp, color = Forma.colors.accent)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Rest, ${state.restRemaining} remaining" },
        ) {
            Text(state.restRemaining ?: "", style = Forma.type.numberHuge.maxScale(1.4f), color = Forma.colors.text)
            Text("rest remaining", style = Forma.type.supporting, color = Forma.colors.textSecondary)
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("+15 sec", holder::addRest, Modifier.weight(1f), icon = FormaIcons.Plus, enabled = !state.paused)
        SecondaryButton("Skip rest", holder::skipRest, Modifier.weight(1f), icon = FormaIcons.Skip)
    }
    Spacer(Modifier.height(10.dp))
    if (state.paused) SecondaryButton("Resume", holder::resume, icon = FormaIcons.Play)
    else SecondaryButton("Pause", holder::pause, icon = FormaIcons.Pause)
    state.next?.let {
        Spacer(Modifier.height(20.dp))
        NextCard(it.label, it.name, it.detail)
    }
    Spacer(Modifier.height(10.dp))
    Text("Logged: ${state.progressDescription}.", style = Forma.type.caption, color = Forma.colors.textMuted)
    if (state.canUndo) {
        app.forma.ui.components.QuietButton("Undo last set", holder::undo, icon = FormaIcons.Undo, color = Forma.colors.textSecondary)
    }
}

@Composable
private fun FinishedContent(holder: PlayerStateHolder) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        FormaIllustration(Illustration.COMPLETE, width = 180.dp, accent = Forma.colors.pastels.green.container)
        Spacer(Modifier.height(16.dp))
        Text("Every set is logged", style = Forma.type.sectionTitle, color = Forma.colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Finish to save the session and tell us how it felt.", style = Forma.type.body, color = Forma.colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Finish workout", holder::finish, icon = FormaIcons.Check)
        app.forma.ui.components.QuietButton("Undo last set", holder::undo, icon = FormaIcons.Undo, color = Forma.colors.textSecondary)
    }
}

@Composable
private fun Sheets(state: PlayerUiState, holder: PlayerStateHolder) {
    when (val sheet = state.sheet) {
        PlayerSheet.Menu -> FormaBottomSheet(onDismissRequest = holder::closeSheet, title = "Workout") {
            if (state.paused) SettingsRow("Resume", holder::resume, icon = FormaIcons.Play)
            else SettingsRow("Pause", holder::pause, icon = FormaIcons.Pause, description = "Stops the timers.")
            SettingsRow("Save and exit", holder::saveAndExit, icon = FormaIcons.Back, description = "Keep this session and resume it later from Today.")
            SettingsRow("Finish workout", holder::requestFinish, icon = FormaIcons.Check, description = "Save what you've done. Unfinished sets stay unfinished.")
            Divider()
            SettingsRow("Discard session", holder::askDiscard, icon = FormaIcons.Trash, destructive = true, description = "Remove this session and its logged sets.")
        }
        PlayerSheet.Overview -> FormaBottomSheet(onDismissRequest = holder::closeSheet, title = "This workout") {
            state.items.forEachIndexed { i, item ->
                if (i > 0) Divider()
                SettingsRow(
                    title = item.name,
                    onClick = { holder.jumpTo(item.index) },
                    description = "${item.detail}\n${item.status}${if (item.current) " · current" else ""}",
                    icon = if (item.done >= item.total) FormaIcons.Check else if (item.current) FormaIcons.Play else null,
                )
            }
        }
        PlayerSheet.Skip -> FormaBottomSheet(onDismissRequest = holder::closeSheet, title = "Skip") {
            Text("Skipped sets are recorded as not done, never as completed.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            SettingsRow("Skip this set", holder::skipSet, icon = FormaIcons.Skip)
            SettingsRow("Skip this exercise", holder::skipExercise, icon = FormaIcons.Skip, description = "Move on to the next exercise.")
            SettingsRow("Exclude permanently", holder::askExclude, icon = FormaIcons.Trash, destructive = true, description = "Never include it again. You can restore it in You.")
        }
        is PlayerSheet.Replace -> ReplaceSheet(sheet.options, sheet.none, onPick = holder::replace, onOmit = holder::skipExercise, onDismiss = holder::closeSheet)
        is PlayerSheet.Easier -> FormaBottomSheet(onDismissRequest = holder::closeSheet, title = "Make it easier") {
            val description = sheet.description
            if (description != null) {
                Text("For the rest of this workout: ${description.replaceFirstChar { c -> c.lowercase() }}.", style = Forma.type.body, color = Forma.colors.text)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Make it easier", holder::makeEasier, icon = FormaIcons.Easier)
            } else {
                NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.INFO, sheet.unavailable ?: "No easier option is available."))
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Replace instead", holder::openReplace, icon = FormaIcons.Swap)
            }
        }
        is PlayerSheet.ConfirmExclude -> ConfirmDialog(
            title = "Exclude ${sheet.name}?",
            body = "It won't appear in future workouts until you restore it in You › Excluded exercises. Today it will be replaced if something fits, or skipped.",
            confirmLabel = "Exclude",
            onConfirm = holder::exclude,
            onDismiss = holder::closeSheet,
        )
        is PlayerSheet.ConfirmFinish -> ConfirmDialog(
            title = "Finish now?",
            body = "You've done ${sheet.done} of ${sheet.total} sets. The session will be saved as partial; unfinished sets won't be marked as done.",
            confirmLabel = "Save as partial",
            onConfirm = holder::finish,
            onDismiss = holder::closeSheet,
            dismissLabel = "Keep going",
        )
        PlayerSheet.ConfirmDiscard -> ConfirmDialog(
            title = "Discard this session?",
            body = "This removes the session and any sets you logged in it. Your other history is not affected.",
            confirmLabel = "Discard",
            onConfirm = holder::discard,
            onDismiss = holder::closeSheet,
            destructive = true,
        )
        null -> Unit
    }
}
