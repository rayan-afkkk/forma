package app.forma.core.domain

import app.forma.core.model.ActiveSessionState
import app.forma.core.model.Load
import app.forma.core.model.PlayerPhase
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.Target
import app.forma.core.model.TimerState
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutSession

sealed interface PlayerAction {
    /** Logs the current set with what was actually done. */
    data class CompleteSet(val reps: Int? = null, val seconds: Int? = null, val load: Load? = null) : PlayerAction
    data object StartWorkTimer : PlayerAction
    data object PauseWorkTimer : PlayerAction
    data object ResetWorkTimer : PlayerAction
    data class AddRest(val seconds: Int) : PlayerAction
    data object SkipRest : PlayerAction
    data object Pause : PlayerAction
    data object Resume : PlayerAction
    data object SkipSet : PlayerAction
    data object SkipExercise : PlayerAction
    data class JumpTo(val itemIndex: Int) : PlayerAction
    data object UndoLastSet : PlayerAction
    data class Draft(val reps: Int?, val load: Load?) : PlayerAction
}

/** What a transition changes outside the active state. */
data class Transition(
    val state: ActiveSessionState,
    val upsertSets: List<SetLog> = emptyList(),
    val deleteSet: Pair<String, Int>? = null,
    val skipped: Set<String>? = null,
)

/**
 * The workout player as a pure state machine. All timers are computed from stored wall-clock
 * timestamps, so the state survives rotation, backgrounding and process death. Nothing is ever
 * marked complete automatically: only [PlayerAction.CompleteSet] logs a set.
 */
object PlayerMachine {

    fun initial(session: WorkoutSession, now: Long): ActiveSessionState {
        val first = firstIncomplete(session, session.skippedItemKeys, 0)
        return ActiveSessionState(
            sessionId = session.id,
            itemIndex = first?.first ?: 0,
            setNumber = first?.second ?: 1,
            phase = if (first == null) PlayerPhase.FINISHED else PlayerPhase.WORK,
            workTimer = first?.let { workTimerFor(session.plan.items[it.first]) },
            startedAt = session.startedAt,
            updatedAt = now,
        )
    }

    /** Applies time-based transitions: a finished rest becomes work. Never logs anything. */
    fun normalize(state: ActiveSessionState, session: WorkoutSession, now: Long): ActiveSessionState {
        val rest = state.restTimer
        if (state.phase == PlayerPhase.REST && !state.paused && rest != null && rest.isDone(now)) {
            return state.copy(phase = PlayerPhase.WORK, restTimer = null, updatedAt = now)
        }
        if (state.phase != PlayerPhase.FINISHED && state.itemIndex !in session.plan.items.indices) {
            return state.copy(phase = PlayerPhase.FINISHED, updatedAt = now)
        }
        return state
    }

    fun currentItem(state: ActiveSessionState, session: WorkoutSession): WorkoutItem? =
        session.plan.items.getOrNull(state.itemIndex)

    fun reduce(state: ActiveSessionState, session: WorkoutSession, action: PlayerAction, now: Long): Transition {
        val s = normalize(state, session, now)
        val items = session.plan.items
        val item = items.getOrNull(s.itemIndex)
        return when (action) {
            is PlayerAction.CompleteSet -> {
                if (item == null || s.phase == PlayerPhase.FINISHED) return Transition(s)
                val set = SetLog(
                    sessionId = session.id,
                    itemKey = item.key,
                    setNumber = s.setNumber,
                    exerciseId = item.exerciseId,
                    exerciseName = item.exerciseName,
                    reps = if (item.target is Target.Reps) (action.reps ?: defaultReps(item, session, s)) else null,
                    seconds = if (item.target is Target.Time) (action.seconds ?: timedSeconds(s, item, now)) else null,
                    load = if (item.dumbbellUse != null) (action.load ?: s.draftLoad ?: item.load) else null,
                    completedAt = now,
                )
                val logged = session.sets.filterNot { it.id == set.id } + set
                val sessionAfter = session.copy(sets = logged)
                val next = nextPosition(sessionAfter, s.itemIndex, s.setNumber)
                val sameItem = next != null && next.first == s.itemIndex
                val restSeconds = item.restSeconds
                val newState = when {
                    next == null -> s.copy(phase = PlayerPhase.FINISHED, restTimer = null, workTimer = null)
                    else -> {
                        val nextItem = items[next.first]
                        s.copy(
                            itemIndex = next.first,
                            setNumber = next.second,
                            phase = if (restSeconds > 0) PlayerPhase.REST else PlayerPhase.WORK,
                            restTimer = if (restSeconds > 0) TimerState(restSeconds * 1000L, 0, if (s.paused) null else now) else null,
                            workTimer = workTimerFor(nextItem),
                            draftReps = if (sameItem) set.reps else null,
                            draftLoad = if (sameItem) set.load else null,
                        )
                    }
                }
                Transition(newState.copy(updatedAt = now), upsertSets = listOf(set))
            }

            PlayerAction.StartWorkTimer -> {
                if (s.paused || item == null) return Transition(s)
                val timer = s.workTimer ?: workTimerFor(item) ?: return Transition(s)
                Transition(s.copy(workTimer = timer.start(now), phase = PlayerPhase.WORK, restTimer = null, updatedAt = now))
            }

            PlayerAction.PauseWorkTimer -> Transition(s.copy(workTimer = s.workTimer?.stop(now), updatedAt = now))

            PlayerAction.ResetWorkTimer -> Transition(s.copy(workTimer = item?.let { workTimerFor(it) }, updatedAt = now))

            is PlayerAction.AddRest -> {
                val rest = s.restTimer ?: return Transition(s)
                Transition(s.copy(restTimer = rest.extend(action.seconds * 1000L), updatedAt = now))
            }

            PlayerAction.SkipRest -> Transition(s.copy(phase = PlayerPhase.WORK, restTimer = null, updatedAt = now))

            PlayerAction.Pause -> {
                if (s.paused) return Transition(s)
                Transition(
                    s.copy(
                        paused = true,
                        pausedAt = now,
                        restTimer = s.restTimer?.stop(now),
                        workTimer = s.workTimer?.stop(now),
                        resumeRestTimer = s.restTimer?.isRunning == true,
                        resumeWorkTimer = s.workTimer?.isRunning == true,
                        updatedAt = now,
                    ),
                )
            }

            PlayerAction.Resume -> {
                if (!s.paused) return Transition(s)
                val pausedFor = s.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0
                Transition(
                    s.copy(
                        paused = false,
                        pausedAt = null,
                        totalPausedMs = s.totalPausedMs + pausedFor,
                        restTimer = if (s.resumeRestTimer) s.restTimer?.start(now) else s.restTimer,
                        workTimer = if (s.resumeWorkTimer) s.workTimer?.start(now) else s.workTimer,
                        resumeRestTimer = false,
                        resumeWorkTimer = false,
                        updatedAt = now,
                    ),
                )
            }

            PlayerAction.SkipSet -> {
                if (item == null) return Transition(s)
                // Moving on without logging. The set stays not done; it is never recorded as completed.
                val next = nextPositionAfterSkip(session, s.itemIndex, s.setNumber)
                moveTo(s, session, next, now)
            }

            PlayerAction.SkipExercise -> {
                if (item == null) return Transition(s)
                val skipped = session.skippedItemKeys + item.key
                val next = firstIncomplete(session, skipped, s.itemIndex + 1) ?: firstIncomplete(session, skipped, 0)
                val t = moveTo(s, session.copy(skippedItemKeys = skipped), next, now)
                t.copy(skipped = skipped)
            }

            is PlayerAction.JumpTo -> {
                if (action.itemIndex !in items.indices) return Transition(s)
                val target = items[action.itemIndex]
                val skipped = session.skippedItemKeys - target.key
                val done = session.setsFor(target.key).size
                val setNumber = (done + 1).coerceAtMost(target.sets)
                Transition(
                    s.copy(
                        itemIndex = action.itemIndex,
                        setNumber = setNumber,
                        phase = PlayerPhase.WORK,
                        restTimer = null,
                        workTimer = workTimerFor(target),
                        draftReps = null,
                        draftLoad = null,
                        updatedAt = now,
                    ),
                    skipped = if (skipped != session.skippedItemKeys) skipped else null,
                )
            }

            PlayerAction.UndoLastSet -> {
                val last = session.sets.maxByOrNull { it.completedAt } ?: return Transition(s)
                val index = items.indexOfFirst { it.key == last.itemKey }
                if (index < 0) return Transition(s)
                Transition(
                    s.copy(
                        itemIndex = index,
                        setNumber = last.setNumber,
                        phase = PlayerPhase.WORK,
                        restTimer = null,
                        workTimer = workTimerFor(items[index]),
                        draftReps = last.reps,
                        draftLoad = last.load,
                        updatedAt = now,
                    ),
                    deleteSet = last.itemKey to last.setNumber,
                )
            }

            is PlayerAction.Draft -> Transition(s.copy(draftReps = action.reps, draftLoad = action.load, updatedAt = now))
        }
    }

    private fun moveTo(s: ActiveSessionState, session: WorkoutSession, next: Pair<Int, Int>?, now: Long): Transition {
        if (next == null) return Transition(s.copy(phase = PlayerPhase.FINISHED, restTimer = null, workTimer = null, updatedAt = now))
        val nextItem = session.plan.items[next.first]
        return Transition(
            s.copy(
                itemIndex = next.first,
                setNumber = next.second,
                phase = PlayerPhase.WORK,
                restTimer = null,
                workTimer = workTimerFor(nextItem),
                draftReps = if (next.first == s.itemIndex) s.draftReps else null,
                draftLoad = if (next.first == s.itemIndex) s.draftLoad else null,
                updatedAt = now,
            ),
        )
    }

    /** Position after logging set [setNumber] of item [index]. */
    private fun nextPosition(session: WorkoutSession, index: Int, setNumber: Int): Pair<Int, Int>? {
        val item = session.plan.items[index]
        val done = session.setsFor(item.key).map { it.setNumber }.toSet()
        val remaining = (1..item.sets).firstOrNull { it !in done && it > setNumber } ?: (1..item.sets).firstOrNull { it !in done }
        if (remaining != null) return index to remaining
        return firstIncomplete(session, session.skippedItemKeys, index + 1) ?: firstIncomplete(session, session.skippedItemKeys, 0)
    }

    private fun nextPositionAfterSkip(session: WorkoutSession, index: Int, setNumber: Int): Pair<Int, Int>? {
        val item = session.plan.items[index]
        val done = session.setsFor(item.key).map { it.setNumber }.toSet()
        val laterSet = ((setNumber + 1)..item.sets).firstOrNull { it !in done }
        if (laterSet != null) return index to laterSet
        return firstIncomplete(session, session.skippedItemKeys + item.key, index + 1)
    }

    /** First item at or after [from] that still has sets to do and is not skipped. */
    fun firstIncomplete(session: WorkoutSession, skipped: Set<String>, from: Int): Pair<Int, Int>? {
        val items = session.plan.items
        for (i in from until items.size) {
            val item = items[i]
            if (item.key in skipped) continue
            val done = session.setsFor(item.key).map { it.setNumber }.toSet()
            val next = (1..item.sets).firstOrNull { it !in done } ?: continue
            return i to next
        }
        return null
    }

    private fun workTimerFor(item: WorkoutItem): TimerState? = when (val t = item.target) {
        is Target.Time -> TimerState(targetMs = t.seconds * 1000L * (if (item.perSide) 2 else 1))
        is Target.Reps -> null
    }

    private fun timedSeconds(s: ActiveSessionState, item: WorkoutItem, now: Long): Int {
        val target = (item.target as Target.Time).seconds
        val elapsed = s.workTimer?.elapsedMs(now) ?: 0
        // If the timer was not used, the person confirmed the set as done: record the target.
        if (elapsed <= 0) return target
        val perSideFactor = if (item.perSide) 2 else 1
        return ((elapsed / 1000) / perSideFactor).toInt().coerceAtLeast(1)
    }

    /** Suggested reps for the current set: the last set of this exercise in this session, else mid-range. */
    fun defaultReps(item: WorkoutItem, session: WorkoutSession, state: ActiveSessionState): Int {
        state.draftReps?.let { return it }
        session.setsFor(item.key).lastOrNull()?.reps?.let { return it }
        val t = item.target as? Target.Reps ?: return 0
        return (t.min + t.max + 1) / 2
    }

    fun defaultLoad(item: WorkoutItem, session: WorkoutSession, state: ActiveSessionState): Load? =
        state.draftLoad ?: session.setsFor(item.key).lastOrNull()?.load ?: item.load

    /** Completed when every planned set was logged and nothing was skipped; otherwise partial. */
    fun statusFor(session: WorkoutSession): SessionStatus {
        val allDone = session.plan.items.all { item -> session.setsFor(item.key).size >= item.sets }
        return if (allDone) SessionStatus.COMPLETED else SessionStatus.PARTIAL
    }
}
