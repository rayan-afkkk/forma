package app.forma.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.NoteKind
import app.forma.presentation.Format
import app.forma.presentation.NoteUi
import app.forma.presentation.you.EquipmentEditorStateHolder
import app.forma.presentation.you.EquipmentListStateHolder
import app.forma.presentation.you.ExclusionsStateHolder
import app.forma.presentation.you.GoalsStateHolder
import app.forma.presentation.you.ScheduleStateHolder
import app.forma.presentation.you.SettingsStateHolder
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.components.ChoiceCard
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.Divider
import app.forma.ui.components.FilterChip
import app.forma.ui.components.FormaCard
import app.forma.ui.components.FormaTextField
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PageHeader
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.QuietButton
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SettingsGroup
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.ToggleRow
import app.forma.ui.components.TopBar
import app.forma.ui.icons.FormaIcons
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberNotificationPermission
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.common.EquipmentForm
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout
import app.forma.ui.theme.maxScale
import java.time.DayOfWeek

/** Standard frame for a settings sub-screen: back button, serif title, scrolling content. */
@Composable
fun SettingsScaffold(title: String, subtitle: String? = null, bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(onBack = navigator::back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Layout.screenPadding).padding(bottom = 32.dp),
        ) {
            PageHeader(title, subtitle = subtitle)
            Spacer(Modifier.height(16.dp))
            content()
        }
        if (bottom != null) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = Layout.screenPadding, vertical = 12.dp)) { bottom() }
        }
    }
}

// ------------------------------------------------------------------------------------------ goals

@Composable
fun GoalsRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("goals") { GoalsStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    val profile = state.profile
    SettingsScaffold("Goals and experience", "Changes apply to your next planned session.") {
        FormaTextField(profile.name, holder::setName, "Name (optional)", placeholder = "What should we call you?", capitalizeWords = true)
        Spacer(Modifier.height(24.dp))
        SectionLabel("Goal")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { ChoiceCard(it.label, profile.goal == it, { holder.setGoal(it) }, description = it.description) }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Experience")
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperienceLevel.entries.forEach { ChoiceCard(it.label, profile.experience == it, { holder.setExperience(it) }, description = experienceDescription(it)) }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Usual session length")
        Spacer(Modifier.height(10.dp))
        MinutesChips(profile.sessionMinutes, holder::setMinutes)
        Spacer(Modifier.height(24.dp))
        SectionLabel("Your space")
        FormaCard(padding = 16.dp) {
            ToggleRow("Quiet workouts", profile.quiet, holder::setQuiet, description = "No jumping or stomping.", icon = FormaIcons.Quiet)
            Divider()
            ToggleRow("Limited space", profile.limitedSpace, holder::setLimitedSpace, description = "Exercises that stay in one spot.", icon = FormaIcons.Space)
        }
    }
}

fun experienceDescription(level: ExperienceLevel) = when (level) {
    ExperienceLevel.NEW -> "Little or no regular strength training."
    ExperienceLevel.SOME -> "You've trained on and off and know the basic moves."
    ExperienceLevel.EXPERIENCED -> "You train regularly and are comfortable with dumbbell work."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinutesChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 20, 25, 30, 45).forEach { FilterChip("$it min", it == selected, { onSelect(it) }, icon = FormaIcons.Clock) }
    }
}

// ------------------------------------------------------------------------------------------ schedule

/** Seven day toggles. Selected days are filled and carry a check mark for screen readers and colour-blind users. */
@Composable
fun DayPicker(days: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { day ->
            val selected = day in days
            val dow = DayOfWeek.of(day)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (selected) Forma.colors.primary else Color.Transparent)
                        .border(1.5.dp, if (selected) Forma.colors.primary else Forma.colors.borderStrong, CircleShape)
                        .toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle(day) })
                        .semantics { contentDescription = Format.dayName(dow) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Format.dayLetter(dow), style = Forma.type.label.maxScale(1.3f), color = if (selected) Forma.colors.onPrimary else Forma.colors.text)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Forma.colors.text else Color.Transparent))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("schedule") { ScheduleStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    val requestPermission = rememberNotificationPermission { granted ->
        if (granted) holder.setReminders(true) else holder.permissionDenied()
    }
    SettingsScaffold("Schedule", "Choose the days that usually suit you. If you miss one, your plan simply continues.") {
        SectionLabel("Training days")
        Spacer(Modifier.height(10.dp))
        DayPicker(state.days, holder::toggleDay)
        Spacer(Modifier.height(8.dp))
        Text("${Format.count(state.days.size, "session")} a week · ${Format.days(state.days)}", style = Forma.type.supporting, color = Forma.colors.textSecondary)
        state.error?.let {
            Spacer(Modifier.height(10.dp))
            NoteCard(NoteUi(NoteKind.INFO, it))
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("Reminders")
        Spacer(Modifier.height(8.dp))
        FormaCard(padding = 16.dp) {
            ToggleRow(
                "Workout reminders",
                state.remindersEnabled,
                { on -> if (on) requestPermission() else holder.setReminders(false) },
                description = "One gentle notification on planned days. Nothing on rest days. Needs permission to show notifications.",
                icon = FormaIcons.Bell,
            )
            if (state.remindersEnabled) {
                Divider()
                Spacer(Modifier.height(12.dp))
                Text("Time", style = Forma.type.bodyStrong, color = Forma.colors.text)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 * 60, 8 * 60 + 30, 12 * 60, 17 * 60 + 30, 18 * 60, 19 * 60 + 30).forEach { minute ->
                        FilterChip(Format.timeOfDay(minute), minute == state.reminderMinute, { holder.setReminderTime(minute) })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ sound & haptics

@Composable
fun SoundAndHapticsRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("sound") { SettingsStateHolder(it, services) }
    val settings by holder.state.collectUiState()
    SettingsScaffold("Sound, vibration and screen") {
        FormaCard(padding = 16.dp) {
            ToggleRow("Sound when a rest ends", settings.sound, holder::setSound, icon = FormaIcons.Sound, description = "A short tone at the end of rests and timed sets.")
            Divider()
            ToggleRow("Vibration", settings.haptics, holder::setHaptics, icon = FormaIcons.Vibrate, description = "A short vibration at the same moments.")
            Divider()
            ToggleRow("Keep screen on during workouts", settings.keepScreenOn, holder::setKeepScreenOn, icon = FormaIcons.Today, description = "Only while a workout is running. The screen can sleep again when you pause or finish.")
        }
    }
}

// ------------------------------------------------------------------------------------------ equipment

@Composable
fun EquipmentListRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("equipment") { EquipmentListStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    SettingsScaffold("Equipment", "Workouts only use what's in your active profile.") {
        SettingsGroup {
            state.profiles.forEachIndexed { i, profile ->
                if (i > 0) Divider()
                SettingsRow(
                    title = profile.name + if (profile.active) " (active)" else "",
                    onClick = { holder.edit(profile.id) },
                    icon = if (profile.active) FormaIcons.Check else FormaIcons.Dumbbell,
                    description = profile.summary,
                    trailing = if (!profile.active && state.profiles.size > 1) {
                        { QuietButton("Use", { holder.select(profile.id) }) }
                    } else null,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.canAdd) {
            SecondaryButton("Add an equipment profile", holder::add, icon = FormaIcons.Plus)
        } else {
            FormaCard(color = Forma.colors.surfaceSecondary) {
                ProBadge()
                Spacer(Modifier.height(8.dp))
                Text(state.addLockedReason.orEmpty(), style = Forma.type.supporting, color = Forma.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("See Pro", holder::add, icon = FormaIcons.Sparkle)
            }
        }
    }
}

@Composable
fun EquipmentEditorRoute(profileId: String?) {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("equipment-editor:$profileId") { EquipmentEditorStateHolder(it, services, profileId) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    if (state.loading) return
    SettingsScaffold(
        title = if (state.isNew) "New equipment" else "Edit equipment",
        bottom = { PrimaryButton("Save", holder::save, enabled = state.canSave) },
    ) {
        FormaTextField(state.name, holder::setName, "Profile name", placeholder = "Home", capitalizeWords = true)
        Spacer(Modifier.height(20.dp))
        EquipmentForm(state.draft, holder::setDraft)
        if (state.canDelete) {
            Spacer(Modifier.height(20.dp))
            QuietButton("Delete this profile", holder::askDelete, icon = FormaIcons.Trash, color = Forma.colors.danger)
        }
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Delete ${state.name}?",
            body = "This removes the equipment profile. Your workout history is not affected.",
            confirmLabel = "Delete",
            onConfirm = holder::delete,
            onDismiss = holder::cancelDelete,
            destructive = true,
        )
    }
}

// ------------------------------------------------------------------------------------------ exclusions

@Composable
fun ExclusionsRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("exclusions") { ExclusionsStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    SettingsScaffold("Excluded exercises", "Excluded exercises never appear in your workouts, whatever program you follow, until you restore them.") {
        if (state.excluded.isEmpty()) {
            FormaCard(color = Forma.colors.surfaceSecondary) {
                Text("Nothing is excluded.", style = Forma.type.bodyStrong, color = Forma.colors.text)
                Text("You can exclude an exercise from any workout, or from the list below.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
            }
        } else {
            SettingsGroup {
                state.excluded.forEachIndexed { i, row ->
                    if (i > 0) Divider()
                    SettingsRow(row.name, null, description = row.detail, trailing = { QuietButton("Restore", { holder.restore(row.exerciseId) }, icon = FormaIcons.Undo) })
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (!state.browsing) {
            SecondaryButton("Exclude another exercise", { holder.setBrowsing(true) }, icon = FormaIcons.Plus)
        } else {
            SectionLabel("All exercises")
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                state.available.forEachIndexed { i, row ->
                    if (i > 0) Divider()
                    SettingsRow(row.name, null, description = row.detail, trailing = { QuietButton("Exclude", { holder.exclude(row.exerciseId) }) })
                }
            }
        }
    }
}
