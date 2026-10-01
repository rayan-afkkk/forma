package app.forma.presentation.today

import app.forma.core.domain.PlanKey
import app.forma.core.domain.StartResult
import app.forma.core.domain.TodayDay
import app.forma.core.domain.TodayModel
import app.forma.core.engine.DayState
import app.forma.core.engine.ReturnStatus
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.ProFeature
import app.forma.core.model.SessionStatus
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Format
import app.forma.presentation.PlanMapper
import app.forma.presentation.PlanUi
import app.forma.presentation.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

data class DayUi(
    val letter: String,
    val dayNumber: Int,
    val state: DayState,
    val isToday: Boolean,
    val planned: Boolean,
    /** Spoken description, e.g. "Wednesday, planned, done". Selection is never conveyed by colour alone. */
    val description: String,
)

data class WeekUi(val days: List<DayUi>, val summary: String)

data class ResumeUi(val sessionId: String, val title: String, val progressText: String, val fraction: Float)

data class ReturnUi(val title: String, val body: String, val recommended: Boolean)

sealed interface TodayCard {
    data class Planned(val plan: PlanUi, val movedNote: String?) : TodayCard
    data class Done(val title: String, val body: String, val sessionId: String, val next: String?) : TodayCard
    data class Rest(val title: String, val body: String, val next: String?, val plan: PlanUi?) : TodayCard
}

data class TimeOption(val minutes: Int, val label: String)

data class AdjustUi(
    val timeOptions: List<TimeOption>,
    val selectedMinutes: Int,
    val easier: Boolean,
    val quiet: Boolean,
    val equipmentOptions: List<Pair<String, String>>,
    val selectedEquipmentId: String,
    val equipmentLocked: Boolean,
    val previewDuration: String,
    val previewCount: String,
    val previewNotes: List<String>,
    val impractical: String?,
    val moveTargets: List<Pair<LocalDate, String>>,
    val canMove: Boolean,
    val changed: Boolean,
)

data class TodayUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val dateText: String = "",
    val week: WeekUi? = null,
    val resume: ResumeUi? = null,
    val returnCheckIn: ReturnUi? = null,
    val card: TodayCard? = null,
    val upgradeOffer: Boolean = false,
    val sampleNote: String? = null,
    val adjust: AdjustUi? = null,
    val confirmDiscard: Boolean = false,
)

class TodayStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<TodayUiState>(scope, TodayUiState()) {

    private val planning = services.planning
    private var latest: TodayModel? = null
    private var draft: PlanAdjustments = PlanAdjustments()

    init {
        launch {
            planning.observeToday().collect { model ->
                latest = model
                update { it.copy(loading = false) + model }
            }
        }
    }

    private operator fun TodayUiState.plus(model: TodayModel): TodayUiState {
        val hour = Instant.ofEpochMilli(services.clock.nowMillis()).atZone(services.clock.zone()).hour
        val inProgress = model.inProgress
        val card = when (val day = model.day) {
            is TodayDay.Planned -> TodayCard.Planned(
                PlanMapper.toUi(day.plan, services.catalog, PlanKey.ProgramNext.value),
                movedNote = if (day.moved) "Moved here from another day." else null,
            )
            is TodayDay.Done -> {
                val session = day.sessions.first()
                val partial = day.sessions.all { it.status == SessionStatus.PARTIAL }
                TodayCard.Done(
                    title = if (partial) "Partly done today" else "Done for today",
                    body = "${session.plan.title} · ${Format.count(session.completedSetCount, "set")} logged." +
                        if (partial) " Every set you did counts." else " Nice work.",
                    sessionId = session.id,
                    next = nextText(day.nextDate, model.today, day.next?.plan?.title),
                )
            }
            is TodayDay.Rest -> TodayCard.Rest(
                title = "Rest day",
                body = "Nothing is planned today. Rest is part of training. If you'd like to move, a short mobility session is a gentle option.",
                next = nextText(day.nextDate, model.today, day.next?.plan?.title),
                plan = day.next?.let { PlanMapper.toUi(it, services.catalog, PlanKey.ProgramNext.value) },
            )
        }
        val returnUi = when (val r = model.returnStatus) {
            ReturnStatus.None -> null
            is ReturnStatus.ShortBreak -> ReturnUi(
                title = "Welcome back",
                body = "It's been ${r.daysAway} days since your last session. Would you like your next session to be a little easier?",
                recommended = false,
            )
            is ReturnStatus.LongBreak -> ReturnUi(
                title = "Welcome back",
                body = "It's been about ${(r.daysAway + 3) / 7} weeks since your last session. Your history is all here. " +
                    "We suggest easing back in with lighter versions of your next two sessions.",
                recommended = true,
            )
        }
        val sampleNote = when {
            model.access.isPro -> null
            model.access.sampleRemaining > 0 && model.access.sampleRemaining < 3 ->
                "${Format.count(model.access.sampleRemaining, "adaptive session")} left in your free sample."
            else -> null
        }
        return copy(
            greeting = Format.greeting(hour, model.name),
            dateText = Format.longDate(model.today),
            week = WeekUi(
                days = model.week.days.map { d ->
                    val state = when (d.state) {
                        DayState.DONE -> "done"
                        DayState.PARTIAL -> "partly done"
                        DayState.PLANNED -> "planned"
                        DayState.NOT_DONE -> "planned, not done"
                        DayState.REST -> "rest"
                    }
                    DayUi(
                        letter = Format.dayLetter(d.date.dayOfWeek),
                        dayNumber = d.date.dayOfMonth,
                        state = d.state,
                        isToday = d.isToday,
                        planned = d.planned,
                        description = "${Format.dayName(d.date.dayOfWeek)}${if (d.isToday) ", today" else ""}, $state",
                    )
                },
                summary = model.week.accessibilityText,
            ),
            resume = inProgress?.let {
                ResumeUi(
                    sessionId = it.id,
                    title = it.plan.title,
                    progressText = "${it.completedSetCount} of ${it.plannedSetCount} sets done",
                    fraction = if (it.plannedSetCount == 0) 0f else it.completedSetCount.toFloat() / it.plannedSetCount,
                )
            },
            returnCheckIn = returnUi,
            card = card,
            upgradeOffer = model.upgradeOfferDue,
            sampleNote = sampleNote,
        )
    }

    private fun nextText(date: LocalDate?, today: LocalDate, title: String?): String? {
        date ?: return null
        val name = title ?: "Your next session"
        return "Next: $name, ${Format.relativeDay(date, today)}."
    }

    // ------------------------------------------------------------------------------------ actions

    fun start() = launch {
        when (val result = services.sessions.start(PlanKey.ProgramNext)) {
            is StartResult.Started -> navigate(Destination.Player(result.sessionId))
            is StartResult.AlreadyActive -> {
                message("You have a session in progress. Resume it or finish it first.")
            }
            StartResult.NeedsPro -> navigate(Destination.Paywall("program"))
            is StartResult.NothingToDo -> message(result.reason)
        }
    }

    fun preview() = navigate(Destination.Preview(PlanKey.ProgramNext.value))

    fun resume() {
        val id = current.resume?.sessionId ?: return
        launch { services.sessions.resumed() }
        navigate(Destination.Player(id))
    }

    fun askDiscard() = update { it.copy(confirmDiscard = true) }
    fun cancelDiscard() = update { it.copy(confirmDiscard = false) }

    /** Saves a session with logged sets as partial, or discards an empty one. */
    fun endInProgress(save: Boolean) {
        val id = current.resume?.sessionId ?: return
        update { it.copy(confirmDiscard = false) }
        launch {
            if (save) {
                when (services.sessions.finish(id)) {
                    app.forma.core.domain.FinishResult.NothingLogged -> {
                        services.sessions.discard(id)
                        message("Nothing was logged, so the session was removed.")
                    }
                    else -> navigate(Destination.Summary(id, justFinished = true))
                }
            } else {
                services.sessions.discard(id)
                message("Session discarded.")
            }
        }
    }

    fun answerReturn(easeBack: Boolean) = launch {
        planning.answerReturnCheckIn(easeBack)
        message(if (easeBack) "Your next session will be a little easier." else "Your plan continues as normal.")
    }

    fun dismissUpgrade() = launch { services.repos.userState.update { it.copy(upgradeOfferSeen = true) } }

    fun openUpgrade() {
        dismissUpgrade()
        navigate(Destination.Paywall("sample_complete"))
    }

    fun openSummary(sessionId: String) = navigate(Destination.Summary(sessionId, justFinished = false))

    fun startMobility() = navigate(Destination.Preview(PlanKey.Template("mobility_reset").value))

    // ------------------------------------------------------------------------------------ adjust today

    fun openAdjust() = launch {
        draft = planning.adjustments(PlanKey.ProgramNext)
        refreshAdjust()
    }

    fun closeAdjust() = update { it.copy(adjust = null) }

    fun setMinutes(minutes: Int) = changeDraft { it.copy(targetMinutes = minutes) }
    fun setEasier(easier: Boolean) = changeDraft { it.copy(easier = easier) }
    fun setQuiet(quiet: Boolean) = changeDraft { it.copy(quiet = quiet) }
    fun setEquipment(id: String) = changeDraft { it.copy(equipmentProfileId = id) }

    private fun changeDraft(transform: (PlanAdjustments) -> PlanAdjustments) = launch {
        draft = transform(draft)
        refreshAdjust()
    }

    private suspend fun refreshAdjust() {
        val s = planning.snapshot()
        val saved = planning.adjustments(PlanKey.ProgramNext)
        val preview = planning.previewAdjustments(PlanKey.ProgramNext, draft)
        val usual = s.user.profile.sessionMinutes
        val options = buildList {
            add(TimeOption(FULL_SESSION, "Full"))
            listOf(10, 15, 20, 30, 45).forEach { add(TimeOption(it, if (it == usual) "$it min · usual" else "$it min")) }
            if (usual !in listOf(10, 15, 20, 30, 45)) add(TimeOption(usual, "$usual min · usual"))
        }.sortedBy { if (it.minutes == FULL_SESSION) Int.MAX_VALUE else it.minutes }
        val canSwitch = s.access.can(ProFeature.MULTIPLE_EQUIPMENT_PROFILES)
        val plannedToday = latest?.day is TodayDay.Planned
        val previewPlan = preview?.plan
        update {
            it.copy(
                adjust = AdjustUi(
                    timeOptions = options,
                    selectedMinutes = draft.targetMinutes ?: usual,
                    easier = draft.easier,
                    quiet = draft.quiet ?: s.user.profile.quiet,
                    equipmentOptions = s.equipment.map { p -> p.id to p.name },
                    selectedEquipmentId = planning.activeEquipment(s, draft).id,
                    equipmentLocked = !canSwitch,
                    previewDuration = previewPlan?.let { p -> Format.aboutMinutes(p.estimatedMinutes) } ?: "",
                    previewCount = previewPlan?.let { p ->
                        "${Format.count(p.items.count { i -> i.phase == app.forma.core.model.Phase.MAIN }, "exercise")} · ${Format.count(p.totalSets, "set")}"
                    } ?: "",
                    previewNotes = previewPlan?.notes.orEmpty()
                        .filter { n -> n.kind != app.forma.core.model.NoteKind.SUMMARY }
                        .map { n -> n.text },
                    impractical = preview?.impractical?.let { i ->
                        "${i.requestedMinutes} minutes is too short for meaningful main work. The shortest practical version is about ${i.shortestMinutes} min. " +
                            "For a very short session, try the 10-minute mobility reset in Explore."
                    },
                    moveTargets = if (plannedToday) planning.moveTargets(s).map { d -> d to Format.relativeDay(d, s.today).replaceFirstChar { c -> c.uppercase() } } else emptyList(),
                    canMove = plannedToday && s.inProgress == null,
                    changed = draft != saved,
                ),
            )
        }
    }

    fun applyAdjust() = launch {
        planning.setAdjustments(PlanKey.ProgramNext, draft)
        update { it.copy(adjust = null) }
        message("Today's plan is updated.")
    }

    fun resetAdjust() = launch {
        draft = PlanAdjustments()
        refreshAdjust()
    }

    fun reviewExercises() {
        update { it.copy(adjust = null) }
        navigate(Destination.Preview(PlanKey.ProgramNext.value))
    }

    fun moveTo(date: LocalDate) = launch {
        val today = services.clock.today()
        planning.moveSession(today, date)
        update { it.copy(adjust = null) }
        message("Moved to ${Format.relativeDay(date, today)}.")
    }

    companion object {
        /** "Full session": no time limit beyond the session itself. */
        const val FULL_SESSION = 180
    }
}
