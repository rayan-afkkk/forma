package app.forma.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.forma.core.model.NoteKind
import app.forma.presentation.PlanUi
import app.forma.presentation.today.AdjustUi
import app.forma.presentation.today.ReturnUi
import app.forma.presentation.today.TodayCard
import app.forma.presentation.today.TodayStateHolder
import app.forma.presentation.today.TodayUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.components.Badge
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.FilterChip
import app.forma.ui.components.FormaBottomSheet
import app.forma.ui.components.FormaCard
import app.forma.ui.components.LoadingState
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PageHeader
import app.forma.ui.components.PastelCard
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.ProgressBar
import app.forma.ui.components.QuietButton
import app.forma.ui.components.ScreenColumn
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.ToggleRow
import app.forma.ui.components.WeekStrip
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.common.PlanMeta
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Pastel
import app.forma.ui.theme.Shapes

@Composable
fun TodayRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("today") { TodayStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    TodayScreen(state, holder)
}

@Composable
fun TodayScreen(state: TodayUiState, holder: TodayStateHolder) {
    if (state.loading) {
        LoadingState()
        return
    }
    ScreenColumn(Modifier.statusBarsPadding()) {
        PageHeader("Today", eyebrow = state.greeting)
        Text(state.dateText, style = Forma.type.supporting, color = Forma.colors.textMuted)
        Spacer(Modifier.height(20.dp))
        state.week?.let { WeekStrip(it) }
        Spacer(Modifier.height(24.dp))

        state.resume?.let { resume ->
            FormaCard {
                SectionLabel("In progress")
                Spacer(Modifier.height(6.dp))
                Text(resume.title, style = Forma.type.cardTitle, color = Forma.colors.text)
                Spacer(Modifier.height(10.dp))
                ProgressBar(resume.fraction, resume.progressText)
                Spacer(Modifier.height(6.dp))
                Text(resume.progressText, style = Forma.type.supporting, color = Forma.colors.textSecondary)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Resume workout", holder::resume, icon = FormaIcons.Play)
                QuietButton("End session", holder::askDiscard, Modifier.align(Alignment.CenterHorizontally), color = Forma.colors.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }

        state.returnCheckIn?.let {
            ReturnCard(it, holder)
            Spacer(Modifier.height(16.dp))
        }

        when (val card = state.card) {
            is TodayCard.Planned -> PlannedCard(card.plan, card.movedNote, holder, enabled = state.resume == null)
            is TodayCard.Done -> DoneCard(card, holder)
            is TodayCard.Rest -> RestCard(card, holder)
            null -> Unit
        }

        state.sampleNote?.let {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(FormaIcons.Sparkle, contentDescription = null, tint = Forma.colors.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = Forma.type.caption, color = Forma.colors.textMuted)
            }
        }

        if (state.upgradeOffer) {
            Spacer(Modifier.height(16.dp))
            FormaCard {
                ProBadge()
                Spacer(Modifier.height(10.dp))
                Text("Your free adaptive sessions are done", style = Forma.type.cardTitleSmall, color = Forma.colors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "You can keep training with Foundations, logging and seeing your history for free. Pro keeps adjusting each session to what you log.",
                    style = Forma.type.supporting, color = Forma.colors.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton("See what Pro includes", holder::openUpgrade)
                QuietButton("Not now", holder::dismissUpgrade, Modifier.align(Alignment.CenterHorizontally), color = Forma.colors.textSecondary)
            }
        }
    }

    state.adjust?.let { AdjustSheet(it, holder) }

    if (state.confirmDiscard) {
        val resume = state.resume
        ConfirmDialog(
            title = "End this session?",
            body = if (resume != null && resume.fraction > 0f) {
                "Save it as a partial session to keep the ${resume.progressText.substringBefore(" of")} sets you logged, or discard it. Discarding removes this session and its sets."
            } else {
                "Nothing has been logged yet, so this session will be removed."
            },
            confirmLabel = if (resume != null && resume.fraction > 0f) "Save as partial" else "Remove",
            onConfirm = { holder.endInProgress(save = resume != null && resume.fraction > 0f) },
            onDismiss = holder::cancelDiscard,
            dismissLabel = "Keep it",
        )
    }
}

@Composable
private fun ReturnCard(r: ReturnUi, holder: TodayStateHolder) {
    val pastel = Forma.colors.pastels.lavender
    PastelCard(pastel) {
        Text(r.title, style = Forma.type.cardTitle, color = pastel.content)
        Spacer(Modifier.height(8.dp))
        Text(r.body, style = Forma.type.body, color = pastel.secondary)
        Spacer(Modifier.height(16.dp))
        PastelButton(if (r.recommended) "Ease back in (suggested)" else "Make it easier", pastel) { holder.answerReturn(true) }
        Spacer(Modifier.height(8.dp))
        PastelButton("Continue as planned", pastel, outlined = true) { holder.answerReturn(false) }
    }
}

/** A dark pill on a pastel card: the pastel's own content colour keeps contrast high. */
@Composable
fun PastelButton(text: String, pastel: Pastel, modifier: Modifier = Modifier, outlined: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(Shapes.pill)
            .let { if (outlined) it.background(pastel.content.copy(alpha = 0.07f)) else it.background(pastel.content) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (outlined) pastel.content else pastel.container
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = Forma.type.label, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlannedCard(plan: PlanUi, movedNote: String?, holder: TodayStateHolder, enabled: Boolean) {
    val pastel = Forma.colors.pastels.blue
    PastelCard(pastel, onClick = holder::preview, onClickLabel = "See the exercises") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.eyebrow, style = Forma.type.supporting, color = pastel.secondary, modifier = Modifier.weight(1f))
            if (plan.adjusted) Badge("Adjusted", icon = FormaIcons.Sliders, color = pastel.content, background = pastel.content.copy(alpha = 0.08f))
        }
        Spacer(Modifier.height(6.dp))
        Text(plan.title, style = Forma.type.sectionTitle, color = pastel.content)
        Spacer(Modifier.height(6.dp))
        Text(plan.purpose, style = Forma.type.body, color = pastel.secondary)
        Spacer(Modifier.height(14.dp))
        PlanMeta(plan, color = pastel.content)
        plan.why?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(FormaIcons.Info, contentDescription = null, tint = pastel.secondary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text("Why this workout: $it", style = Forma.type.supporting, color = pastel.secondary)
            }
        }
        movedNote?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Forma.type.caption, color = pastel.secondary)
        }
        Spacer(Modifier.height(18.dp))
        if (enabled) {
            PastelButton("Start workout", pastel, icon = FormaIcons.Play, onClick = holder::start)
        } else {
            Text("Finish or end the session in progress before starting another.", style = Forma.type.supporting, color = pastel.secondary)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Adjust today", holder::openAdjust, Modifier.weight(1f), icon = FormaIcons.Sliders)
        SecondaryButton("Exercises", holder::preview, Modifier.weight(1f), icon = FormaIcons.Overview)
    }
    val important = plan.notes.filter { it.kind in setOf(NoteKind.CONSTRAINT, NoteKind.RETURN, NoteKind.PRO_LIMIT, NoteKind.SHORTENED) }
    important.forEach {
        Spacer(Modifier.height(10.dp))
        NoteCard(it)
    }
    plan.impractical?.let {
        Spacer(Modifier.height(10.dp))
        NoteCard(app.forma.presentation.NoteUi(NoteKind.SHORTENED, it))
    }
}

@Composable
private fun DoneCard(card: TodayCard.Done, holder: TodayStateHolder) {
    val pastel = Forma.colors.pastels.green
    PastelCard(pastel) {
        Text(card.title, style = Forma.type.sectionTitle, color = pastel.content)
        Spacer(Modifier.height(8.dp))
        Text(card.body, style = Forma.type.body, color = pastel.secondary)
        card.next?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Forma.type.supporting, color = pastel.secondary)
        }
        Spacer(Modifier.height(16.dp))
        PastelButton("View summary", pastel) { holder.openSummary(card.sessionId) }
    }
    Spacer(Modifier.height(12.dp))
    SecondaryButton("Gentle mobility session", holder::startMobility, icon = FormaIcons.Leaf)
}

@Composable
private fun RestCard(card: TodayCard.Rest, holder: TodayStateHolder) {
    val pastel = Forma.colors.pastels.lavender
    PastelCard(pastel) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(card.title, style = Forma.type.sectionTitle, color = pastel.content)
                Spacer(Modifier.height(8.dp))
                Text(card.body, style = Forma.type.body, color = pastel.secondary)
            }
        }
        card.next?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Forma.type.supporting, color = pastel.content)
        }
        Spacer(Modifier.height(16.dp))
        PastelButton("10-minute mobility reset", pastel, icon = FormaIcons.Leaf, onClick = holder::startMobility)
    }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FormaIllustration(Illustration.REST, width = 150.dp, accent = Forma.colors.pastels.lavender.container)
    }
    if (card.plan != null) {
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Do the next session anyway", holder::preview, icon = FormaIcons.Play)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustSheet(adjust: AdjustUi, holder: TodayStateHolder) {
    FormaBottomSheet(onDismissRequest = holder::closeAdjust, title = "Adjust today") {
        Text("Changes apply to today's session only.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
        Spacer(Modifier.height(18.dp))
        SectionLabel("Time available")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            adjust.timeOptions.forEach { option ->
                FilterChip(option.label, option.minutes == adjust.selectedMinutes, { holder.setMinutes(option.minutes) })
            }
        }
        Spacer(Modifier.height(14.dp))
        FormaCard(color = Forma.colors.surfaceSecondary, padding = 16.dp) {
            Text("Preview", style = Forma.type.eyebrow, color = Forma.colors.textMuted)
            Spacer(Modifier.height(4.dp))
            Text("${adjust.previewDuration} · ${adjust.previewCount}", style = Forma.type.bodyStrong, color = Forma.colors.text)
            adjust.previewNotes.forEach {
                Spacer(Modifier.height(6.dp))
                Text(it, style = Forma.type.supporting, color = Forma.colors.textSecondary)
            }
        }
        adjust.impractical?.let {
            Spacer(Modifier.height(10.dp))
            NoteCard(app.forma.presentation.NoteUi(NoteKind.SHORTENED, it))
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("Easier session", adjust.easier, holder::setEasier, description = "One fewer set on main exercises and a little more rest.", icon = FormaIcons.Easier)
        ToggleRow("Quiet workout", adjust.quiet, holder::setQuiet, description = "No jumping or stomping today.", icon = FormaIcons.Quiet)

        if (adjust.equipmentOptions.size > 1 || adjust.equipmentLocked) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Equipment", Modifier.weight(1f))
                if (adjust.equipmentLocked) ProBadge()
            }
            Spacer(Modifier.height(10.dp))
            if (adjust.equipmentLocked) {
                Text(
                    "Using ${adjust.equipmentOptions.firstOrNull { it.first == adjust.selectedEquipmentId }?.second ?: "your equipment"}. Switching equipment for a day is part of Pro.",
                    style = Forma.type.supporting, color = Forma.colors.textSecondary,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    adjust.equipmentOptions.forEach { (id, name) ->
                        FilterChip(name, id == adjust.selectedEquipmentId, { holder.setEquipment(id) }, icon = FormaIcons.Dumbbell)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsRow("Review exercises", holder::reviewExercises, icon = FormaIcons.Overview, description = "Replace, simplify or leave out individual exercises.")

        if (adjust.canMove && adjust.moveTargets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Move this session")
            Spacer(Modifier.height(6.dp))
            Text("Your plan moves with it. Nothing piles up.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                adjust.moveTargets.forEach { (date, label) -> FilterChip(label, false, { holder.moveTo(date) }, icon = FormaIcons.Calendar) }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Apply to today", holder::applyAdjust, enabled = adjust.changed)
        QuietButton("Reset to the planned session", holder::resetAdjust, Modifier.align(Alignment.CenterHorizontally), color = Forma.colors.textSecondary)
    }
}
