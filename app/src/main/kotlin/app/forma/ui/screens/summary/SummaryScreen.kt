package app.forma.ui.screens.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forma.core.model.Difficulty
import app.forma.core.model.NoteKind
import app.forma.core.model.format
import app.forma.presentation.NoteUi
import app.forma.presentation.summary.SessionSummaryStateHolder
import app.forma.presentation.summary.SummaryUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.components.ChoiceCard
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaBottomSheet
import app.forma.ui.components.FormaCard
import app.forma.ui.components.FormaTextField
import app.forma.ui.components.LoadingState
import app.forma.ui.components.MissingState
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.QuietButton
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.StatTile
import app.forma.ui.components.Stepper
import app.forma.ui.components.TopBar
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout

@Composable
fun SummaryRoute(sessionId: String, justFinished: Boolean) {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("summary:$sessionId") { SessionSummaryStateHolder(it, services, sessionId, justFinished) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    SummaryScreen(state, holder)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(state: SummaryUiState, holder: SessionSummaryStateHolder) {
    val navigator = LocalNavigator.current
    if (state.loading) {
        LoadingState()
        return
    }
    if (state.missing) {
        MissingState("This session is no longer in your history.", navigator::back)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (!state.justFinished) TopBar(onBack = holder::done)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Layout.screenPadding).padding(bottom = 24.dp),
        ) {
            if (state.justFinished) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FormaIllustration(Illustration.COMPLETE, width = 150.dp, accent = Forma.colors.pastels.green.container)
                }
            }
            Text(state.heading, style = Forma.type.pageTitle, color = Forma.colors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.justFinished) "${state.title} · ${state.dateText}" else state.dateText,
                style = Forma.type.supporting, color = Forma.colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Active time", state.durationText, Modifier.widthIn(min = 100.dp))
                StatTile("Sets logged", state.setsText, Modifier.widthIn(min = 100.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(state.exercisesText, style = Forma.type.supporting, color = Forma.colors.textSecondary)
            if (state.justFinished) {
                Spacer(Modifier.height(12.dp))
                Text(state.encouragement, style = Forma.type.body, color = Forma.colors.text)
            }
            if (state.partial) {
                Spacer(Modifier.height(12.dp))
                NoteCard(NoteUi(NoteKind.INFO, "Saved as a partial session. Unfinished sets aren't counted as done, and the next plan uses what you actually did."))
            }
            state.sampleNotice?.let {
                Spacer(Modifier.height(12.dp))
                NoteCard(NoteUi(NoteKind.PRO_LIMIT, it))
            }
            if (state.showUpgrade) {
                Spacer(Modifier.height(10.dp))
                FormaCard {
                    ProBadge()
                    Spacer(Modifier.height(8.dp))
                    Text("Keep sessions adapting with Pro", style = Forma.type.cardTitleSmall, color = Forma.colors.text)
                    Text("Optional. Nothing you've logged will be locked.", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("See Pro", holder::upgrade, icon = FormaIcons.Sparkle)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("How did it feel?")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    ChoiceCard(d.label, state.difficulty == d, { holder.setDifficulty(d) }, description = d.description)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(state.feedbackExplanation, style = Forma.type.caption, color = Forma.colors.textMuted)
            Spacer(Modifier.height(16.dp))
            FormaTextField(state.note, holder::setNote, "Note (optional)", placeholder = "Anything to remember next time?")

            Spacer(Modifier.height(24.dp))
            SectionLabel("What you did")
            state.exercises.forEach { ex ->
                Spacer(Modifier.height(10.dp))
                FormaCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = Forma.type.bodyStrong, color = Forma.colors.text)
                            Text("Planned: ${ex.planned}", style = Forma.type.caption, color = Forma.colors.textMuted)
                        }
                        Text(ex.status, style = Forma.type.caption, color = Forma.colors.textSecondary)
                    }
                    ex.sets.forEach { set ->
                        Divider(Modifier.padding(vertical = 2.dp))
                        SettingsRow(set.text, { holder.editSet(ex.key, set.setNumber) }, icon = FormaIcons.Edit)
                    }
                    if (ex.sets.size < ex.plannedSets + 3) {
                        QuietButton("Add a set", { holder.addSet(ex.key) }, icon = FormaIcons.Plus, color = Forma.colors.textSecondary)
                    }
                }
            }
            state.nextText?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, style = Forma.type.body, color = Forma.colors.textSecondary)
            }
            Spacer(Modifier.height(18.dp))
            QuietButton("Delete this session", holder::askDeleteSession, icon = FormaIcons.Trash, color = Forma.colors.danger)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Layout.screenPadding, vertical = 12.dp)) {
            PrimaryButton("Done", holder::done)
        }
    }

    state.edit?.let { edit ->
        FormaBottomSheet(onDismissRequest = holder::cancelEdit, title = "Set ${edit.setNumber}") {
            Text(edit.exerciseName, style = Forma.type.body, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(16.dp))
            Stepper(
                value = edit.value.toString(),
                label = if (edit.timed) "seconds" else "reps",
                onMinus = { holder.changeEditValue(if (edit.timed) -5 else -1) },
                onPlus = { holder.changeEditValue(if (edit.timed) 5 else 1) },
                large = false,
            )
            edit.load?.let { load ->
                Spacer(Modifier.height(12.dp))
                Stepper(
                    value = load.format(),
                    label = "weight",
                    onMinus = { holder.changeEditLoad(false) },
                    onPlus = { holder.changeEditLoad(true) },
                    large = false,
                    minusDescription = "Lighter weight",
                    plusDescription = "Heavier weight",
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save set", holder::saveEdit)
            if (edit.existing) {
                QuietButton("Remove this set", holder::deleteEditedSet, Modifier.align(Alignment.CenterHorizontally), icon = FormaIcons.Trash, color = Forma.colors.danger)
            }
        }
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Delete this session?",
            body = "This removes the session and all of its logged sets from your history. Use it for a session recorded by mistake. It can't be undone.",
            confirmLabel = "Delete",
            onConfirm = holder::deleteSession,
            onDismiss = holder::cancelDeleteSession,
            destructive = true,
        )
    }
}
