package app.forma.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.presentation.onboarding.OnboardingStateHolder
import app.forma.presentation.onboarding.OnboardingStep
import app.forma.presentation.onboarding.OnboardingUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.components.ChoiceCard
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaCard
import app.forma.ui.components.FormaTextField
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PastelCard
import app.forma.ui.components.PillSegmentedControl
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProgressBar
import app.forma.ui.components.QuietButton
import app.forma.ui.components.RoundIconButton
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.ToggleRow
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.PlatformBackHandler
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.common.EquipmentForm
import app.forma.ui.screens.common.PlanMeta
import app.forma.ui.screens.common.PlanSections
import app.forma.ui.screens.you.DayPicker
import app.forma.ui.screens.you.MinutesChips
import app.forma.ui.screens.you.experienceDescription
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout

@Composable
fun OnboardingRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("onboarding") { OnboardingStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    OnboardingScreen(state, holder)
}

@Composable
fun OnboardingScreen(state: OnboardingUiState, holder: OnboardingStateHolder) {
    PlatformBackHandler(enabled = state.step != OnboardingStep.WELCOME) { holder.back() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (state.step != OnboardingStep.WELCOME) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(FormaIcons.Back, "Previous step", holder::back)
                Box(Modifier.weight(1f).padding(end = Layout.screenPadding)) {
                    ProgressBar(
                        state.questionIndex.toFloat() / state.questionCount,
                        "Step ${state.questionIndex} of ${state.questionCount}",
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Layout.screenPadding).padding(bottom = 24.dp),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> Welcome()
                OnboardingStep.NAME -> Question("What should we call you?", "Optional. It stays on this device.") {
                    FormaTextField(state.name, holder::setName, "Your name", placeholder = "First name", capitalizeWords = true)
                }
                OnboardingStep.GOAL -> Question("What's your main goal?", "This shapes your rep ranges and session length. You can change it later.") {
                    Goal.entries.forEach { ChoiceCard(it.label, state.goal == it, { holder.setGoal(it) }, description = it.description) }
                }
                OnboardingStep.EXPERIENCE -> Question("How much training have you done?", "We'll start at a level that suits you and adjust from what you log.") {
                    ExperienceLevel.entries.forEach { ChoiceCard(it.label, state.experience == it, { holder.setExperience(it) }, description = experienceDescription(it)) }
                }
                OnboardingStep.EQUIPMENT -> Question("What equipment do you have?", "Workouts only ever use what you list here.") {
                    EquipmentForm(state.equipment, holder::setEquipment)
                }
                OnboardingStep.TIME -> Question("How long is a typical session?", "Plans are built to fit. On busy days you can shorten a session with Adjust today.") {
                    MinutesChips(state.minutes, holder::setMinutes)
                }
                OnboardingStep.SCHEDULE -> Question("Which days usually suit you?", "Missing a day is fine. Your plan simply continues on the next one.") {
                    SectionLabel("Sessions per week")
                    PillSegmentedControl(listOf(2, 3, 4, 5), state.days.size.coerceIn(2, 5), { "$it" }, holder::setFrequency)
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Days")
                    DayPicker(state.days, holder::toggleDay)
                }
                OnboardingStep.CONSTRAINTS -> Question("Anything about your space?", "These are always respected.") {
                    FormaCard(padding = 16.dp) {
                        ToggleRow("Quiet workouts", state.quiet, holder::setQuiet, description = "No jumping or stomping. Good for flats and sleeping households.", icon = FormaIcons.Quiet)
                        Divider()
                        ToggleRow("Limited space", state.limitedSpace, holder::setLimitedSpace, description = "Exercises that stay in one spot, with room to lie on a mat.", icon = FormaIcons.Space)
                    }
                }
                OnboardingStep.SAFETY -> Safety()
                OnboardingStep.PREVIEW -> Preview(state)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = Layout.screenPadding, vertical = 12.dp)) {
            when (state.step) {
                OnboardingStep.WELCOME -> PrimaryButton("Get started", holder::next)
                OnboardingStep.NAME -> {
                    PrimaryButton("Continue", holder::next)
                    if (state.name.isBlank()) QuietButton("Skip", holder::next, Modifier.align(Alignment.CenterHorizontally), color = Forma.colors.textSecondary)
                }
                OnboardingStep.SAFETY -> PrimaryButton("I understand", holder::next)
                OnboardingStep.PREVIEW -> {
                    PrimaryButton("Start this workout", { holder.finish(startNow = true) }, enabled = state.canContinue, icon = FormaIcons.Play, busy = state.finishing)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Go to Today", { holder.finish(startNow = false) }, enabled = state.canContinue)
                }
                else -> PrimaryButton("Continue", holder::next, enabled = state.canContinue)
            }
        }
    }
}

@Composable
private fun Question(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(10.dp))
    Text(subtitle, style = Forma.type.body, color = Forma.colors.textSecondary)
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FormaIllustration(Illustration.WELCOME, width = 220.dp)
    }
    Spacer(Modifier.height(32.dp))
    Text("Open the app. Get a workout you can actually do.", style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(14.dp))
    Text(
        "Tell us what equipment you have, how much time you've got and what suits your space. Forma builds each session around it and learns from what you log.",
        style = Forma.type.body, color = Forma.colors.textSecondary,
    )
    Spacer(Modifier.height(12.dp))
    Text("No account needed. About a minute to set up.", style = Forma.type.supporting, color = Forma.colors.textMuted)
}

@Composable
private fun Safety() {
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FormaIllustration(Illustration.SAFETY, width = 170.dp, accent = Forma.colors.pastels.rose.container)
    }
    Spacer(Modifier.height(20.dp))
    Text("Before you start", style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(14.dp))
    Text(
        "Forma offers general fitness guidance, not medical advice.",
        style = Forma.type.bodyStrong, color = Forma.colors.text,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Move within a comfortable range and stop if you feel pain, dizziness, chest discomfort or unusual shortness of breath. " +
            "If you have a health condition, are pregnant, or are recovering from an injury or surgery, check with a qualified health professional first.",
        style = Forma.type.body, color = Forma.colors.textSecondary,
    )
}

@Composable
private fun Preview(state: OnboardingUiState) {
    Spacer(Modifier.height(12.dp))
    Text("Your first workout", style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(10.dp))
    Text("Built from your answers with the same rules as every session. Change anything later in You.", style = Forma.type.body, color = Forma.colors.textSecondary)
    Spacer(Modifier.height(20.dp))
    state.previewError?.let { NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.CONSTRAINT, it)) }
    val plan = state.preview
    if (plan == null) {
        if (state.previewError == null) Text("Building your workout…", style = Forma.type.supporting, color = Forma.colors.textMuted)
        return
    }
    val pastel = Forma.colors.pastels.blue
    PastelCard(pastel) {
        Text(plan.eyebrow, style = Forma.type.supporting, color = pastel.secondary)
        Text(plan.title, style = Forma.type.sectionTitle, color = pastel.content)
        Spacer(Modifier.height(6.dp))
        Text(plan.purpose, style = Forma.type.body, color = pastel.secondary)
        Spacer(Modifier.height(12.dp))
        PlanMeta(plan, color = pastel.content)
    }
    plan.impractical?.let {
        Spacer(Modifier.height(10.dp))
        NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.SHORTENED, it))
    }
    plan.notes.forEach {
        Spacer(Modifier.height(10.dp))
        NoteCard(it)
    }
    PlanSections(plan.sections, onItem = null)
}
