package app.forma.presentation.progress

import app.forma.core.domain.PlanningSnapshot
import app.forma.core.engine.WeekPlanner
import app.forma.core.model.ProFeature
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutSession
import app.forma.core.model.format
import app.forma.core.model.formatAmount
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Format
import app.forma.presentation.StateHolder
import app.forma.presentation.Tab
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import kotlin.math.roundToInt

data class StatUi(val label: String, val value: String)

data class WeekBarUi(val label: String, val completed: Int, val planned: Int, val isCurrent: Boolean, val description: String)

data class MilestoneUi(val title: String, val detail: String)

data class HistoryRowUi(
    val sessionId: String,
    val title: String,
    val date: String,
    val status: String,
    val detail: String,
    val partial: Boolean,
)

data class ChartPoint(val label: String, val value: Float)

/**
 * One exercise's history on a single, clearly labelled metric. Metrics are never mixed: loaded
 * sets use weight, bodyweight sets use reps, holds use seconds.
 */
data class ExerciseProgressUi(
    val exerciseId: String,
    val name: String,
    val metric: String,
    val unit: String,
    val latest: String,
    val best: String,
    val sessions: Int,
    val points: List<ChartPoint>,
    val converted: Boolean,
)

data class ProgressUiState(
    val loading: Boolean = true,
    val empty: Boolean = false,
    val stats: List<StatUi> = emptyList(),
    val weeks: List<WeekBarUi> = emptyList(),
    val weeksNote: String = "",
    val milestones: List<MilestoneUi> = emptyList(),
    val history: List<HistoryRowUi> = emptyList(),
    val exercises: List<ExerciseProgressUi> = emptyList(),
    val trendsLocked: Boolean = true,
)

class ProgressStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<ProgressUiState>(scope, ProgressUiState()) {

    init {
        launch { services.planning.snapshots.collect { set(build(it)) } }
    }

    fun openSession(id: String) = navigate(Destination.Summary(id, justFinished = false))
    fun openToday() = navigate(Destination.Main(Tab.TODAY))
    fun unlock() = navigate(Destination.Paywall("progress"))

    companion object {
        const val WEEKS = 8

        fun build(s: PlanningSnapshot): ProgressUiState {
            val finished = s.finished.sortedByDescending { it.startedAt }
            if (finished.isEmpty()) return ProgressUiState(loading = false, empty = true)
            val unit = s.user.settings.displayUnit
            val complete = finished.count { it.status == SessionStatus.COMPLETED }
            val partial = finished.count { it.status == SessionStatus.PARTIAL }
            val week = WeekPlanner.summary(s.today, s.user.schedule, s.moves, s.sessionDays)

            val weeks = (WEEKS - 1 downTo 0).map { back ->
                val start = WeekPlanner.weekStart(s.today).minusWeeks(back.toLong())
                val end = start.plusDays(6)
                val done = finished.count { LocalDate.parse(it.localDate).let { d -> !d.isBefore(start) && !d.isAfter(end) } }
                val planned = if (back == 0) week.plannedCount else s.user.schedule.sessionsPerWeek
                val label = "${start.dayOfMonth} ${start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
                WeekBarUi(label, done, planned, back == 0, "Week of $label: $done of $planned planned sessions")
            }

            return ProgressUiState(
                loading = false,
                empty = false,
                stats = listOf(
                    StatUi("Sessions completed", complete.toString()),
                    StatUi("Partial sessions", partial.toString()),
                    StatUi("This week", "${week.completedCount} of ${week.plannedCount} planned"),
                ),
                weeks = weeks,
                weeksNote = "Sessions per week. Planned counts for past weeks use your current schedule.",
                milestones = milestones(finished, s, unit),
                history = finished.map { session ->
                    HistoryRowUi(
                        sessionId = session.id,
                        title = session.plan.title,
                        date = Format.shortDate(LocalDate.parse(session.localDate)),
                        status = if (session.status == SessionStatus.COMPLETED) "Complete" else "Partial",
                        detail = "${Format.minutes(session.activeSeconds)} · ${session.completedSetCount} of ${session.plannedSetCount} sets",
                        partial = session.status == SessionStatus.PARTIAL,
                    )
                },
                exercises = exercises(finished, unit),
                trendsLocked = !s.access.can(ProFeature.ADVANCED_PROGRESS),
            )
        }

        private fun exercises(finished: List<WorkoutSession>, unit: WeightUnit): List<ExerciseProgressUi> {
            val chronological = finished.sortedBy { it.startedAt }
            val byExercise = linkedMapOf<String, MutableList<Pair<WorkoutSession, List<SetLog>>>>()
            for (session in chronological) {
                session.sets.groupBy { it.exerciseId }.forEach { (id, sets) ->
                    byExercise.getOrPut(id) { mutableListOf() } += session to sets
                }
            }
            return byExercise.entries
                .sortedByDescending { it.value.size }
                .take(10)
                .mapNotNull { (id, entries) -> exercise(id, entries, unit) }
        }

        private fun exercise(id: String, entries: List<Pair<WorkoutSession, List<SetLog>>>, unit: WeightUnit): ExerciseProgressUi? {
            val name = entries.last().second.last().exerciseName
            val allSets = entries.flatMap { it.second }
            val loaded = allSets.any { it.load != null }
            val timed = !loaded && allSets.any { it.seconds != null }
            fun label(session: WorkoutSession) = LocalDate.parse(session.localDate).let { "${it.dayOfMonth}/${it.monthValue}" }
            return when {
                loaded -> {
                    val converted = allSets.any { it.load != null && it.load!!.unit != unit }
                    val points = entries.mapNotNull { (session, sets) ->
                        sets.mapNotNull { it.load }.maxOrNull()?.let { ChartPoint(label(session), it.amountIn(unit).toFloat()) }
                    }
                    val bestSet = allSets.filter { it.load != null }.maxWithOrNull(compareBy({ it.load!!.grams }, { it.reps ?: 0 }))!!
                    val lastSets = entries.last().second.filter { it.load != null }
                    val lastTop = lastSets.maxWithOrNull(compareBy({ it.load!!.grams }, { it.reps ?: 0 }))
                    ExerciseProgressUi(
                        exerciseId = id, name = name, metric = "Heaviest set", unit = unit.symbol,
                        latest = lastTop?.let { "${lastSets.size} ${if (lastSets.size == 1) "set" else "sets"}, top set ${it.load!!.format(unit)}${it.reps?.let { r -> " × $r" }.orEmpty()}" } ?: "",
                        best = "${bestSet.load!!.format(unit)}${bestSet.reps?.let { r -> " × $r" }.orEmpty()}",
                        sessions = entries.size, points = points, converted = converted,
                    )
                }
                timed -> {
                    val points = entries.map { (session, sets) -> ChartPoint(label(session), (sets.maxOf { it.seconds ?: 0 }).toFloat()) }
                    ExerciseProgressUi(
                        exerciseId = id, name = name, metric = "Longest hold", unit = "seconds",
                        latest = Format.seconds(entries.last().second.maxOf { it.seconds ?: 0 }),
                        best = Format.seconds(allSets.maxOf { it.seconds ?: 0 }),
                        sessions = entries.size, points = points, converted = false,
                    )
                }
                else -> {
                    val points = entries.map { (session, sets) -> ChartPoint(label(session), (sets.maxOf { it.reps ?: 0 }).toFloat()) }
                    ExerciseProgressUi(
                        exerciseId = id, name = name, metric = "Most reps in a set", unit = "reps",
                        latest = Format.count(entries.last().second.maxOf { it.reps ?: 0 }, "rep"),
                        best = Format.count(allSets.maxOf { it.reps ?: 0 }, "rep"),
                        sessions = entries.size, points = points, converted = false,
                    )
                }
            }
        }

        private fun milestones(finished: List<WorkoutSession>, s: PlanningSnapshot, unit: WeightUnit): List<MilestoneUi> {
            val chronological = finished.sortedBy { it.startedAt }
            val result = mutableListOf<MilestoneUi>()
            chronological.firstOrNull()?.let {
                result += MilestoneUi("First session", Format.longDate(LocalDate.parse(it.localDate)))
            }
            for (count in listOf(5, 10, 25, 50, 100, 200)) {
                chronological.getOrNull(count - 1)?.let {
                    result += MilestoneUi("$count sessions", "Reached ${Format.shortDate(LocalDate.parse(it.localDate))}")
                }
            }
            // First full planned week (a past week where completed sessions met the schedule).
            val planned = s.user.schedule.sessionsPerWeek
            if (planned > 0) {
                val byWeek = chronological.groupBy { WeekPlanner.weekStart(LocalDate.parse(it.localDate)) }
                byWeek.entries.sortedBy { it.key }.firstOrNull { it.value.size >= planned }?.let { (start, _) ->
                    result += MilestoneUi("First full week", "Every planned session in the week of ${Format.shortDate(start)}")
                }
            }
            // Load improvements on the same exercise, in the person's display unit.
            val loads = chronological.flatMap { it.sets }.filter { it.load != null }.groupBy { it.exerciseId }
            loads.values.mapNotNull { sets ->
                val first = sets.first().load!!
                val best = sets.maxOf { it.load!! }
                if (best.grams > first.grams + 1) {
                    MilestoneUi(sets.first().exerciseName, "Up from ${first.format(unit)} to ${best.format(unit)}") to (best.grams - first.grams)
                } else {
                    null
                }
            }.sortedByDescending { it.second }.take(3).forEach { result += it.first }
            return result
        }

        fun percent(part: Int, total: Int) = if (total == 0) 0 else (part * 100.0 / total).roundToInt()
        fun amount(value: Double) = formatAmount(value)
    }
}
