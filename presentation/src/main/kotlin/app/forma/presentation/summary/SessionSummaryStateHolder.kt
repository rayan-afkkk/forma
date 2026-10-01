package app.forma.presentation.summary

import app.forma.core.engine.EquipmentLoads
import app.forma.core.engine.WeekPlanner
import app.forma.core.model.Difficulty
import app.forma.core.model.Load
import app.forma.core.model.SessionStatus
import app.forma.core.model.Target
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutSession
import app.forma.core.model.format
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Effect
import app.forma.presentation.Format
import app.forma.presentation.StateHolder
import app.forma.presentation.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class LoggedSetUi(val setNumber: Int, val text: String)

data class SummaryExerciseUi(
    val key: String,
    val name: String,
    val planned: String,
    val sets: List<LoggedSetUi>,
    val plannedSets: Int,
    val skipped: Boolean,
) {
    val status: String
        get() = when {
            sets.isEmpty() && skipped -> "Skipped"
            sets.isEmpty() -> "Not done"
            sets.size < plannedSets -> "${sets.size} of $plannedSets sets"
            else -> "All $plannedSets ${if (plannedSets == 1) "set" else "sets"}"
        }
}

/** Editing one logged set (or adding a missing one). */
data class EditSetUi(
    val itemKey: String,
    val exerciseName: String,
    val setNumber: Int,
    val timed: Boolean,
    val value: Int,
    val load: Load?,
    val loadOptions: List<Load>,
    val existing: Boolean,
)

data class SummaryUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val justFinished: Boolean = false,
    val title: String = "",
    val heading: String = "",
    val dateText: String = "",
    val durationText: String = "",
    val setsText: String = "",
    val exercisesText: String = "",
    val partial: Boolean = false,
    val encouragement: String = "",
    val exercises: List<SummaryExerciseUi> = emptyList(),
    val difficulty: Difficulty? = null,
    val note: String = "",
    val feedbackExplanation: String = "",
    val nextText: String? = null,
    val sampleNotice: String? = null,
    val showUpgrade: Boolean = false,
    val edit: EditSetUi? = null,
    val confirmDelete: Boolean = false,
    val displayUnit: WeightUnit = WeightUnit.KG,
)

/**
 * The completion screen and the history detail screen. Shows what was actually done, never
 * fabricated numbers such as calories, and lets the person correct mistakes.
 */
class SessionSummaryStateHolder(
    scope: CoroutineScope,
    private val services: AppServices,
    private val sessionId: String,
    justFinished: Boolean,
) : StateHolder<SummaryUiState>(scope, SummaryUiState(justFinished = justFinished)) {

    private var session: WorkoutSession? = null
    private var profiles: List<app.forma.core.model.EquipmentProfile> = emptyList()
    private var noteJob: Job? = null
    private var noteEdited = false

    init {
        launch { services.repos.equipment.profiles.collect { profiles = it } }
        launch {
            combine(services.sessions.observe(sessionId), services.planning.snapshots) { s, snapshot -> s to snapshot }
                .collect { (s, snapshot) ->
                    if (s == null) {
                        update { it.copy(loading = false, missing = true) }
                        return@collect
                    }
                    session = s
                    val today = snapshot.today
                    val next = WeekPlanner.nextPlannedDate(today, snapshot.user.schedule, snapshot.moves)
                    val nextPlan = services.planning.generate(snapshot, app.forma.core.domain.PlanKey.ProgramNext, app.forma.core.model.PlanAdjustments())
                    val access = snapshot.access
                    update {
                        it.copy(
                            loading = false,
                            title = s.plan.title,
                            heading = heading(s, it.justFinished),
                            dateText = Format.longDate(LocalDate.parse(s.localDate)),
                            durationText = Format.minutes(s.activeSeconds),
                            setsText = "${s.completedSetCount} of ${s.plannedSetCount} sets",
                            exercisesText = "${s.plan.items.count { i -> s.setsFor(i.key).size >= i.sets }} of ${s.plan.items.size} exercises complete",
                            partial = s.status == SessionStatus.PARTIAL,
                            encouragement = encouragement(s),
                            exercises = s.plan.items.map { item -> exerciseUi(item, s, snapshot.user.settings.displayUnit) },
                            difficulty = s.feedback?.difficulty,
                            note = if (noteEdited) it.note else s.feedback?.note.orEmpty(),
                            feedbackExplanation = "How it felt helps shape your next session: \"Too hard\" eases things off, " +
                                "\"Hard\" keeps the same targets, and finishing comfortably allows a small step up.",
                            nextText = if (next != null && nextPlan != null) {
                                "Next: ${nextPlan.plan.title}, ${Format.relativeDay(next, today)}."
                            } else null,
                            sampleNotice = when {
                                !it.justFinished || access.isPro -> null
                                s.countedAsSample && access.sampleRemaining > 0 ->
                                    "That was one of your free adaptive sessions. ${Format.count(access.sampleRemaining, "session")} left."
                                s.countedAsSample && access.sampleRemaining == 0 ->
                                    "That was the last of your free adaptive sessions. You can keep training, logging and viewing your history for free."
                                else -> null
                            },
                            showUpgrade = it.justFinished && !access.isPro && access.sampleRemaining == 0 && s.countedAsSample,
                            displayUnit = snapshot.user.settings.displayUnit,
                        )
                    }
                }
        }
    }

    private fun heading(s: WorkoutSession, justFinished: Boolean): String = when {
        !justFinished -> s.plan.title
        s.status == SessionStatus.COMPLETED -> "Session complete"
        else -> "Partial session saved"
    }

    private fun encouragement(s: WorkoutSession): String = when {
        s.status == SessionStatus.COMPLETED -> "You did everything you planned today. Consistent sessions like this are what count."
        s.completedSetCount * 2 >= s.plannedSetCount -> "You got most of it done. A shorter session still counts."
        else -> "Some days are short. Every set you logged is saved, and your plan carries on."
    }

    private fun exerciseUi(item: WorkoutItem, s: WorkoutSession, unit: WeightUnit) = SummaryExerciseUi(
        key = item.key,
        name = item.exerciseName,
        planned = Format.itemDetail(item),
        sets = s.setsFor(item.key).map { set ->
            val reps = set.reps
            val seconds = set.seconds
            val value = when {
                reps != null -> Format.count(reps, "rep")
                seconds != null -> Format.seconds(seconds)
                else -> "Done"
            }
            val load = set.load?.let { load ->
                if (load.unit == unit) load.format() else "${load.format()} (${load.format(unit)})"
            }
            LoggedSetUi(set.setNumber, listOfNotNull("Set ${set.setNumber}", value, load).joinToString(" · "))
        },
        plannedSets = item.sets,
        skipped = item.key in s.skippedItemKeys,
    )

    // ------------------------------------------------------------------------------------ feedback

    fun setDifficulty(difficulty: Difficulty) {
        update { it.copy(difficulty = difficulty) }
        launch { services.sessions.saveFeedback(sessionId, difficulty, current.note) }
    }

    fun setNote(note: String) {
        noteEdited = true
        update { it.copy(note = note.take(1000)) }
        noteJob?.cancel()
        noteJob = launch {
            delay(500)
            services.sessions.saveFeedback(sessionId, current.difficulty, current.note)
        }
    }

    fun done() {
        noteJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                launch { services.sessions.saveFeedback(sessionId, current.difficulty, current.note) }
            }
        }
        if (current.justFinished) navigate(Destination.Main(Tab.TODAY), clear = true) else emit(Effect.Back)
    }

    fun upgrade() = navigate(Destination.Paywall("sample_complete"))

    // ------------------------------------------------------------------------------------ corrections

    fun editSet(itemKey: String, setNumber: Int) {
        val s = session ?: return
        val item = s.plan.item(itemKey) ?: return
        val existing = s.setsFor(itemKey).firstOrNull { it.setNumber == setNumber }
        val timed = item.target is Target.Time
        val defaultValue = when (val t = item.target) {
            is Target.Reps -> existing?.reps ?: t.max
            is Target.Time -> existing?.seconds ?: t.seconds
        }
        update {
            it.copy(
                edit = EditSetUi(
                    itemKey = itemKey,
                    exerciseName = item.exerciseName,
                    setNumber = setNumber,
                    timed = timed,
                    value = defaultValue,
                    load = existing?.load ?: item.load,
                    loadOptions = loadOptions(item),
                    existing = existing != null,
                ),
            )
        }
    }

    /** Adds the next missing set for an exercise, e.g. one that was done but not logged. */
    fun addSet(itemKey: String) {
        val s = session ?: return
        val numbers = s.setsFor(itemKey).map { it.setNumber }.toSet()
        val next = generateSequence(1) { it + 1 }.first { it !in numbers }
        editSet(itemKey, next)
    }

    /** Weights the person owns for this exercise; falls back to the planned weight if the profile is gone. */
    private fun loadOptions(item: WorkoutItem): List<Load> {
        val use = item.dumbbellUse ?: return emptyList()
        val profile = profiles.firstOrNull { it.id == session?.plan?.equipmentProfileId }
        return profile?.let { EquipmentLoads.available(it, use) }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(item.load)
    }

    fun changeEditValue(delta: Int) = update { s ->
        val e = s.edit ?: return@update s
        s.copy(edit = e.copy(value = (e.value + delta).coerceIn(0, if (e.timed) 3600 else 200)))
    }

    fun changeEditLoad(heavier: Boolean) = update { s ->
        val e = s.edit ?: return@update s
        val load = e.load ?: return@update s
        val next = if (heavier) EquipmentLoads.nextUp(e.loadOptions, load) else EquipmentLoads.nextDown(e.loadOptions, load)
        s.copy(edit = e.copy(load = next ?: load))
    }

    fun cancelEdit() = update { it.copy(edit = null) }

    fun saveEdit() {
        val e = current.edit ?: return
        update { it.copy(edit = null) }
        launch {
            services.sessions.editSet(
                sessionId, e.itemKey, e.setNumber,
                reps = if (e.timed) null else e.value,
                seconds = if (e.timed) e.value else null,
                load = e.load,
            )
            message("Set ${e.setNumber} of ${e.exerciseName} saved.")
        }
    }

    fun deleteEditedSet() {
        val e = current.edit ?: return
        update { it.copy(edit = null) }
        launch {
            services.sessions.deleteSet(sessionId, e.itemKey, e.setNumber)
            message("Set removed.")
        }
    }

    fun askDeleteSession() = update { it.copy(confirmDelete = true) }
    fun cancelDeleteSession() = update { it.copy(confirmDelete = false) }

    /** For a session recorded by mistake. Removes it and its sets from history. */
    fun deleteSession() = launch {
        update { it.copy(confirmDelete = false) }
        services.sessions.deleteSession(sessionId)
        message("Session removed from your history.")
        navigate(Destination.Main(Tab.PROGRESS), clear = true)
    }
}
