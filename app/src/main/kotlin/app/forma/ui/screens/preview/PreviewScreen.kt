package app.forma.ui.screens.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forma.presentation.ItemUi
import app.forma.presentation.preview.OptionUi
import app.forma.presentation.preview.PreviewSheet
import app.forma.presentation.preview.PreviewStateHolder
import app.forma.presentation.preview.PreviewUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.components.Badge
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.DemonstrationArea
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaBottomSheet
import app.forma.ui.components.FormaCard
import app.forma.ui.components.LoadingState
import app.forma.ui.components.MissingState
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.QuietButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.TopBar
import app.forma.ui.icons.FormaIcons
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.common.PlanMeta
import app.forma.ui.screens.common.PlanSections
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout

@Composable
fun PreviewRoute(planKey: String) {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("preview:$planKey") { PreviewStateHolder(it, services, planKey) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    PreviewScreen(state, holder)
}

@Composable
fun PreviewScreen(state: PreviewUiState, holder: PreviewStateHolder) {
    val navigator = LocalNavigator.current
    if (state.loading) {
        LoadingState()
        return
    }
    val plan = state.plan
    if (state.missing || plan == null) {
        MissingState("This session isn't available.", navigator::back)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(onBack = navigator::back)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Layout.screenPadding)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.eyebrow, style = Forma.type.supporting, color = Forma.colors.textSecondary, modifier = Modifier.weight(1f))
                if (plan.locked) ProBadge()
            }
            Spacer(Modifier.height(4.dp))
            Text(plan.title, style = Forma.type.pageTitle, color = Forma.colors.text)
            Spacer(Modifier.height(8.dp))
            Text(plan.purpose, style = Forma.type.body, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(14.dp))
            PlanMeta(plan)

            plan.why?.let {
                Spacer(Modifier.height(18.dp))
                FormaCard(padding = 16.dp) {
                    Row {
                        Icon(FormaIcons.Info, contentDescription = null, tint = Forma.colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Why this plan", style = Forma.type.bodyStrong, color = Forma.colors.text)
                            Text(it, style = Forma.type.supporting, color = Forma.colors.textSecondary)
                        }
                    }
                }
            }
            plan.impractical?.let {
                Spacer(Modifier.height(10.dp))
                NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.SHORTENED, it))
            }
            plan.notes.forEach {
                Spacer(Modifier.height(10.dp))
                NoteCard(it)
            }
            if (plan.adjusted) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("Adjusted for today", icon = FormaIcons.Sliders)
                    Spacer(Modifier.weight(1f))
                    QuietButton("Undo changes", holder::resetChanges, icon = FormaIcons.Undo)
                }
            }

            PlanSections(plan.sections, onItem = holder::openItem)

            Spacer(Modifier.height(16.dp))
            Text(
                "Tap an exercise to see how it's done, replace it for this workout, or make it easier. " +
                    "Move within a comfortable range and stop if you feel pain, dizziness or unusual breathlessness.",
                style = Forma.type.caption,
                color = Forma.colors.textMuted,
            )
        }
        // Sticky start action above the navigation bar; content scrolls behind nothing.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Forma.colors.background)
                .navigationBarsPadding()
                .padding(horizontal = Layout.screenPadding, vertical = 12.dp),
        ) {
            Divider(Modifier.padding(bottom = 12.dp))
            when {
                plan.locked -> PrimaryButton("Unlock with Pro", holder::unlock, icon = FormaIcons.Sparkle)
                state.inProgressSessionId != null -> {
                    PrimaryButton("Resume session in progress", holder::resumeInProgress, icon = FormaIcons.Play)
                }
                else -> PrimaryButton("Start workout", holder::start, icon = FormaIcons.Play, busy = state.starting, enabled = plan.items.isNotEmpty())
            }
        }
    }

    when (val sheet = state.sheet) {
        is PreviewSheet.Item -> plan.items.firstOrNull { it.key == sheet.key }?.let { ItemSheet(it, sheet, holder) }
        is PreviewSheet.Replace -> ReplaceSheet(sheet.options, sheet.none, onPick = { holder.replace(sheet.key, it) }, onOmit = { holder.omit(sheet.key) }, onDismiss = holder::closeSheet)
        is PreviewSheet.ConfirmExclude -> ConfirmDialog(
            title = "Exclude ${sheet.name}?",
            body = "It won't appear in any future workout until you restore it in You › Excluded exercises. Today's plan will use an alternative where one fits.",
            confirmLabel = "Exclude",
            onConfirm = { holder.exclude(sheet.key) },
            onDismiss = holder::closeSheet,
        )
        null -> Unit
    }
}

@Composable
fun ExerciseDetail(item: ItemUi) {
    DemonstrationArea(item.pattern, item.name)
    Spacer(Modifier.height(14.dp))
    Text(item.detail, style = Forma.type.bodyStrong, color = Forma.colors.text)
    Text(item.rest, style = Forma.type.supporting, color = Forma.colors.textSecondary)
    if (item.summary.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(item.summary, style = Forma.type.body, color = Forma.colors.textSecondary)
    }
    Spacer(Modifier.height(14.dp))
    SectionLabel("Technique")
    item.cues.forEach { cue ->
        Row(Modifier.padding(top = 8.dp)) {
            Box(Modifier.padding(top = 9.dp).size(5.dp).background(Forma.colors.textSecondary, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(cue, style = Forma.type.body, color = Forma.colors.text)
        }
    }
}

@Composable
private fun ItemSheet(item: ItemUi, sheet: PreviewSheet.Item, holder: PreviewStateHolder) {
    FormaBottomSheet(onDismissRequest = holder::closeSheet, title = item.name) {
        ExerciseDetail(item)
        Spacer(Modifier.height(18.dp))
        SectionLabel("For this workout")
        SettingsRow("Replace for this workout", { holder.showReplacements(item.key) }, icon = FormaIcons.Swap, description = "Your preferences stay the same.")
        SettingsRow(
            "Make it easier today",
            if (sheet.easier != null) ({ holder.makeEasier(item.key) }) else null,
            icon = FormaIcons.Easier,
            description = sheet.easier ?: sheet.easierUnavailable,
        )
        SettingsRow("Leave it out today", { holder.omit(item.key) }, icon = FormaIcons.Close)
        Spacer(Modifier.height(8.dp))
        SectionLabel("Always")
        SettingsRow(
            "Exclude permanently",
            { holder.askExclude(item.key) },
            icon = FormaIcons.Trash,
            description = "Never include it in future workouts. You can restore it in You.",
            destructive = true,
        )
    }
}

@Composable
fun ReplaceSheet(options: List<OptionUi>, none: String?, onPick: (String) -> Unit, onOmit: () -> Unit, onDismiss: () -> Unit) {
    FormaBottomSheet(onDismissRequest = onDismiss, title = "Replace for this workout") {
        if (none != null) {
            NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.CONSTRAINT, none))
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Leave it out today", onOmit)
        } else {
            Text("Options keep the same training role where possible and fit your equipment and exclusions.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            options.forEachIndexed { i, option ->
                if (i > 0) Divider()
                SettingsRow(
                    option.name,
                    { onPick(option.exerciseId) },
                    description = "${option.relation} · ${option.reason}",
                )
            }
        }
    }
}
