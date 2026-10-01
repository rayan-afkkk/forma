package app.forma.presentation.player

import app.forma.core.domain.FinishResult
import app.forma.core.domain.InSessionChange
import app.forma.core.domain.PlayerAction
import app.forma.core.domain.PlayerMachine
import app.forma.core.engine.EasierOption
import app.forma.core.engine.EquipmentLoads
import app.forma.core.engine.ReplacementResult
import app.forma.core.model.ActiveSessionState
import app.forma.core.model.AppSettings
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Load
import app.forma.core.model.MovementPattern
import app.forma.core.model.PlayerPhase
import app.forma.core.model.Target
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutSession
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Effect
import app.forma.presentation.Format
import app.forma.presentation.StateHolder
import app.forma.presentation.Tab
import app.forma.presentation.preview.OptionUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class RepsUi(
    val reps: Int,
    val load: Load?,
    val loadText: String?,
    val canLighter: Boolean,
    val canHeavier: Boolean,
)

data class TimedUi(
    val remainingText: String,
    val fraction: Float,
    val running: Boolean,
    val done: Boolean,
    val perSideNote: String?,
)

data class NextUi(val label: String, val name: String, val detail: String)

data class OverviewItemUi(
    val index: Int,
    val name: String,
    val detail: String,
    val done: Int,
    val total: Int,
    val skipped: Boolean,
    val current: Boolean,
) {
    val status: String
        get() = when {
            skipped && done == 0 -> "Skipped"
            skipped -> "$done of $total sets, rest skipped"
            done >= total -> "Done"
            else -> "$done of $total sets"
        }
}

sealed interface PlayerSheet {
    data object Menu : PlayerSheet
    data object Overview : PlayerSheet
    data object Skip : PlayerSheet
    data class Replace(val key: String, val options: List<OptionUi>, val none: String?) : PlayerSheet
    data class Easier(val key: String, val description: String?, val unavailable: String?) : PlayerSheet
    data class ConfirmExclude(val key: String, val name: String) : PlayerSheet
    data class ConfirmFinish(val done: Int, val total: Int) : PlayerSheet
    data object ConfirmDiscard : PlayerSheet
}

data class PlayerUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val elapsed: String = "0:00",
    val setsDone: Int = 0,
    val setsTotal: Int = 0,
    val paused: Boolean = false,
    val phase: PlayerPhase = PlayerPhase.WORK,
    val section: String = "",
    val exerciseKey: String = "",
    val exerciseName: String = "",
    val pattern: MovementPattern = MovementPattern.WARMUP,
    val summary: String = "",
    val cues: List<String> = emptyList(),
    val setLabel: String = "",
    val targetText: String = "",
    val replacedFrom: String? = null,
    val reps: RepsUi? = null,
    val timed: TimedUi? = null,
    val restRemaining: String? = null,
    val restFraction: Float = 0f,
    val next: NextUi? = null,
    val items: List<OverviewItemUi> = emptyList(),
    val sheet: PlayerSheet? = null,
    val keepScreenOn: Boolean = true,
    val canUndo: Boolean = false,
    val busy: Boolean = false,
) {
    val progress: Float get() = if (setsTotal == 0) 0f else setsDone.toFloat() / setsTotal
    val progressDescription: String get() = "$setsDone of $setsTotal sets done"
}

/**
 * The active workout. Every action is persisted before the screen updates, and timers are derived
 * from stored timestamps, so rotation, backgrounding and process death all return to the same place.
 * The ticker only runs while a timer is visible and only updates text: no decorative animation.
 */
class PlayerStateHolder(
    scope: CoroutineScope,
    private val services: AppServices,
    private val sessionId: String,
    private val autoTick: Boolean = true,
) : StateHolder<PlayerUiState>(scope, PlayerUiState()) {

    private var session: WorkoutSession? = null
    private var active: ActiveSessionState? = null
    private var equipment: EquipmentProfile? = null
    private var settings = AppSettings()
    private var ticker: Job? = null
    private var workCueSentFor: String? = null

    init {
        launch {
            services.repos.userState.state.collect {
                settings = it.settings
                update { s -> s.copy(keepScreenOn = it.settings.keepScreenOn) }
            }
        }
        launch { reload() }
    }

    private suspend fun reload() {
        val loaded = services.sessions.loadPlayer(sessionId)
        if (loaded == null) {
            update { it.copy(loading = false, missing = true) }
            return
        }
        session = loaded.first
        active = loaded.second
        equipment = services.repos.equipment.all().firstOrNull { it.id == loaded.first.plan.equipmentProfileId }
        render()
    }

    /** Re-derives everything visible from the stored session and the clock. */
    fun render() {
        val session = session ?: return
        val state = active ?: return
        val now = services.clock.nowMillis()
        val items = session.plan.items
        val item = items.getOrNull(state.itemIndex)
        val rest = state.restTimer
        val inRest = state.phase == PlayerPhase.REST && rest != null
        val work = state.workTimer

        val nextItem = if (item == null) null else nextAfter(session, state)
        update { s ->
            s.copy(
                loading = false,
                missing = false,
                title = session.plan.title,
                elapsed = Format.elapsed(state.activeMs(now)),
                setsDone = session.sets.size,
                setsTotal = session.plannedSetCount,
                paused = state.paused,
                phase = state.phase,
                section = item?.phase?.label.orEmpty(),
                exerciseKey = item?.key.orEmpty(),
                exerciseName = item?.exerciseName.orEmpty(),
                pattern = item?.pattern ?: MovementPattern.WARMUP,
                summary = item?.let { services.catalog.exercise(it.exerciseId)?.summary }.orEmpty(),
                cues = item?.cues.orEmpty(),
                setLabel = item?.let { "Set ${state.setNumber} of ${it.sets}" }.orEmpty(),
                targetText = item?.let { "Target: ${Format.target(it.target, it.perSide)}" }.orEmpty(),
                replacedFrom = item?.replacedFromName,
                reps = item?.takeIf { it.target is Target.Reps }?.let { repsUi(it, session, state) },
                timed = item?.takeIf { it.target is Target.Time }?.let { i ->
                    val timer = work ?: return@let null
                    TimedUi(
                        remainingText = Format.clock(timer.remainingMs(now)),
                        fraction = (timer.elapsedMs(now).toFloat() / timer.targetMs).coerceIn(0f, 1f),
                        running = timer.isRunning,
                        done = timer.isDone(now),
                        perSideNote = if (i.perSide) "Switch sides halfway" else null,
                    )
                },
                restRemaining = if (inRest) Format.clock(rest!!.remainingMs(now)) else null,
                restFraction = if (inRest) (rest!!.remainingMs(now).toFloat() / rest.targetMs).coerceIn(0f, 1f) else 0f,
                next = nextItem,
                items = items.mapIndexed { index, it ->
                    OverviewItemUi(
                        index = index,
                        name = it.exerciseName,
                        detail = Format.itemDetail(it),
                        done = session.setsFor(it.key).size,
                        total = it.sets,
                        skipped = it.key in session.skippedItemKeys,
                        current = index == state.itemIndex && state.phase != PlayerPhase.FINISHED,
                    )
                },
                canUndo = session.sets.isNotEmpty(),
            )
        }
        manageTicker(state, now)
    }

    private fun repsUi(item: WorkoutItem, session: WorkoutSession, state: ActiveSessionState): RepsUi {
        val reps = PlayerMachine.defaultReps(item, session, state)
        val load = if (item.dumbbellUse != null) PlayerMachine.defaultLoad(item, session, state) else null
        val options = loadOptions(item)
        return RepsUi(
            reps = reps,
            load = load,
            loadText = Format.load(load),
            canLighter = load != null && EquipmentLoads.nextDown(options, load) != null,
            canHeavier = load != null && EquipmentLoads.nextUp(options, load) != null,
        )
    }

    private fun loadOptions(item: WorkoutItem): List<Load> {
        val use = item.dumbbellUse ?: return emptyList()
        val profile = equipment ?: return listOfNotNull(item.load)
        return EquipmentLoads.available(profile, use).ifEmpty { listOfNotNull(item.load) }
    }

    private fun nextAfter(session: WorkoutSession, state: ActiveSessionState): NextUi? {
        val item = session.plan.items[state.itemIndex]
        if (state.phase == PlayerPhase.REST) {
            return NextUi("Up next", item.exerciseName, "Set ${state.setNumber} of ${item.sets} · ${Format.target(item.target, item.perSide)}")
        }
        val remainingSets = item.sets - state.setNumber
        if (remainingSets > 0) return NextUi("After this", "${item.exerciseName}, set ${state.setNumber + 1}", Format.rest(item.restSeconds))
        val next = PlayerMachine.firstIncomplete(session, session.skippedItemKeys + item.key, state.itemIndex + 1)
            ?: return NextUi("After this", "Finish", "That's the last set")
        val n = session.plan.items[next.first]
        return NextUi("After this", n.exerciseName, Format.itemDetail(n))
    }

    private fun manageTicker(state: ActiveSessionState, now: Long) {
        val needsTick = !state.paused && (state.phase == PlayerPhase.REST || state.workTimer?.isRunning == true)
        if (!autoTick) return
        if (needsTick && ticker?.isActive != true) {
            ticker = launch {
                while (isActive) {
                    delay(TICK_MS)
                    tick()
                }
            }
        } else if (!needsTick) {
            ticker?.cancel()
            ticker = null
        }
    }

    /** Called by the ticker; also callable directly (tests, or after returning to the screen). */
    suspend fun tick() {
        val session = session ?: return
        val state = active ?: return
        val now = services.clock.nowMillis()
        val rest = state.restTimer
        if (state.phase == PlayerPhase.REST && rest != null && !state.paused && rest.isDone(now)) {
            // The rest is over: move to work. This never logs anything.
            services.sessions.loadPlayer(sessionId)?.let { (s, a) ->
                this.session = s
                active = a
            }
            cue()
        }
        val work = state.workTimer
        val cueKey = "${state.itemIndex}/${state.setNumber}"
        if (state.phase == PlayerPhase.WORK && work != null && work.isRunning && work.isDone(now) && workCueSentFor != cueKey) {
            workCueSentFor = cueKey
            cue()
        }
        render()
    }

    private fun cue() {
        if (settings.sound || settings.haptics) emit(Effect.Cue(sound = settings.sound, haptic = settings.haptics))
    }

    /** Set while a set is being logged, so a fast double tap can never log the next set too. */
    private var completing = false

    private fun dispatch(action: PlayerAction) = launch {
        val result = services.sessions.dispatch(sessionId, action)
        if (action is PlayerAction.CompleteSet) completing = false
        if (result == null) {
            update { it.copy(missing = true) }
            return@launch
        }
        session = result.first
        active = result.second
        render()
    }

    // ------------------------------------------------------------------------------------ set actions

    fun completeSet() {
        val s = current
        if (completing || s.busy || s.phase != PlayerPhase.WORK || s.paused) return
        completing = true
        val reps = s.reps
        dispatch(
            if (reps != null) PlayerAction.CompleteSet(reps = reps.reps, load = reps.load)
            else PlayerAction.CompleteSet(),
        )
    }

    fun changeReps(delta: Int) {
        val reps = current.reps ?: return
        val value = (reps.reps + delta).coerceIn(0, 200)
        dispatch(PlayerAction.Draft(value, reps.load))
    }

    fun changeLoad(heavier: Boolean) {
        val session = session ?: return
        val state = active ?: return
        val item = session.plan.items.getOrNull(state.itemIndex) ?: return
        val reps = current.reps ?: return
        val currentLoad = reps.load ?: return
        val options = loadOptions(item)
        val next = (if (heavier) EquipmentLoads.nextUp(options, currentLoad) else EquipmentLoads.nextDown(options, currentLoad)) ?: return
        dispatch(PlayerAction.Draft(reps.reps, next))
    }

    fun startTimer() = dispatch(PlayerAction.StartWorkTimer)
    fun pauseTimer() = dispatch(PlayerAction.PauseWorkTimer)
    fun resetTimer() = dispatch(PlayerAction.ResetWorkTimer)
    fun addRest() = dispatch(PlayerAction.AddRest(15))
    fun skipRest() = dispatch(PlayerAction.SkipRest)
    fun undo() = dispatch(PlayerAction.UndoLastSet)

    fun pause() {
        update { it.copy(sheet = null) }
        dispatch(PlayerAction.Pause)
    }

    fun resume() = dispatch(PlayerAction.Resume)

    // ------------------------------------------------------------------------------------ sheets

    fun openMenu() = update { it.copy(sheet = PlayerSheet.Menu) }
    fun openOverview() = update { it.copy(sheet = PlayerSheet.Overview) }
    fun openSkip() = update { it.copy(sheet = PlayerSheet.Skip) }
    fun closeSheet() = update { it.copy(sheet = null) }

    /** System back during a workout opens the menu instead of leaving. */
    fun onBack() = if (current.sheet != null) closeSheet() else openMenu()

    fun skipSet() {
        closeSheet()
        dispatch(PlayerAction.SkipSet)
    }

    fun skipExercise() {
        closeSheet()
        dispatch(PlayerAction.SkipExercise)
    }

    fun jumpTo(index: Int) {
        closeSheet()
        dispatch(PlayerAction.JumpTo(index))
    }

    fun openReplace() = launch {
        val key = current.exerciseKey
        val result = services.sessions.replacementOptions(sessionId, key) ?: return@launch
        update {
            it.copy(
                sheet = when (result) {
                    is ReplacementResult.Available -> PlayerSheet.Replace(
                        key, result.options.map { o -> OptionUi(o.exercise.id, o.exercise.name, o.reason, o.relation.label, o.exercise.summary) }, null,
                    )
                    is ReplacementResult.None -> PlayerSheet.Replace(key, emptyList(), result.explanation)
                },
            )
        }
    }

    fun replace(exerciseId: String) {
        val key = (current.sheet as? PlayerSheet.Replace)?.key ?: return
        launch {
            handle(services.sessions.replace(sessionId, key, exerciseId), "Replaced for this workout only.")
        }
    }

    fun openEasier() = launch {
        val key = current.exerciseKey
        val option = services.sessions.easierOption(sessionId, key) ?: return@launch
        update {
            it.copy(
                sheet = PlayerSheet.Easier(
                    key,
                    description = option.takeUnless { o -> o is EasierOption.None }?.description,
                    unavailable = (option as? EasierOption.None)?.description,
                ),
            )
        }
    }

    fun makeEasier() {
        val key = (current.sheet as? PlayerSheet.Easier)?.key ?: return
        launch { handle(services.sessions.makeEasier(sessionId, key), "Made easier for the rest of this workout.") }
    }

    fun askExclude() {
        val key = current.exerciseKey
        update { it.copy(sheet = PlayerSheet.ConfirmExclude(key, current.exerciseName)) }
    }

    fun exclude() {
        val key = (current.sheet as? PlayerSheet.ConfirmExclude)?.key ?: return
        launch { handle(services.sessions.excludeAndReplace(sessionId, key), null) }
    }

    private suspend fun handle(change: InSessionChange, success: String?) {
        update { it.copy(sheet = null) }
        when (change) {
            is InSessionChange.Replaced -> message(success ?: "Excluded from future workouts. Replaced with ${change.exerciseName} today.")
            is InSessionChange.Skipped -> message(change.explanation)
            is InSessionChange.AlreadyStarted -> message(change.explanation)
            InSessionChange.NotFound -> message("That change isn't possible right now.")
        }
        reload()
    }

    // ------------------------------------------------------------------------------------ leaving

    /** Keeps the session in progress so it can be resumed from Today. */
    fun saveAndExit() {
        update { it.copy(sheet = null) }
        dispatch(PlayerAction.Pause)
        emit(Effect.Back)
    }

    fun requestFinish() {
        val s = current
        if (s.setsDone == 0) {
            update { it.copy(sheet = PlayerSheet.ConfirmDiscard) }
        } else if (s.setsDone < s.setsTotal) {
            update { it.copy(sheet = PlayerSheet.ConfirmFinish(s.setsDone, s.setsTotal)) }
        } else {
            finish()
        }
    }

    fun finish() {
        if (current.busy) return
        update { it.copy(busy = true, sheet = null) }
        launch {
            when (val result = services.sessions.finish(sessionId)) {
                is FinishResult.Finished, is FinishResult.AlreadyFinished ->
                    emit(Effect.Navigate(Destination.Summary(sessionId, justFinished = true), replaceCurrent = true))
                FinishResult.NothingLogged -> update { it.copy(sheet = PlayerSheet.ConfirmDiscard) }
                FinishResult.NotFound -> navigate(Destination.Main(Tab.TODAY), clear = true)
            }
            update { it.copy(busy = false) }
        }
    }

    fun askDiscard() = update { it.copy(sheet = PlayerSheet.ConfirmDiscard) }

    fun discard() = launch {
        services.sessions.discard(sessionId)
        update { it.copy(sheet = null) }
        navigate(Destination.Main(Tab.TODAY), clear = true)
    }

    companion object {
        const val TICK_MS = 250L
    }
}
