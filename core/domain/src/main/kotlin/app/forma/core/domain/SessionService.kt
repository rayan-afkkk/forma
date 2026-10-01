package app.forma.core.domain

import app.forma.core.engine.EasierOption
import app.forma.core.engine.ReplacementResult
import app.forma.core.engine.TrainingRules
import app.forma.core.model.ActiveSessionState
import app.forma.core.model.Difficulty
import app.forma.core.model.ExerciseId
import app.forma.core.model.Load
import app.forma.core.model.NoteKind
import app.forma.core.model.Phase
import app.forma.core.model.PlanSource
import app.forma.core.model.SessionFeedback
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.WorkoutPlan
import app.forma.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

sealed interface StartResult {
    data class Started(val sessionId: String) : StartResult

    /** Another session is already in progress. Resume or finish it first. */
    data class AlreadyActive(val sessionId: String) : StartResult
    data object NeedsPro : StartResult
    data class NothingToDo(val reason: String) : StartResult
}

sealed interface FinishResult {
    data class Finished(val session: WorkoutSession) : FinishResult

    /** Already finished earlier (a repeated tap or retry). Nothing changed. */
    data class AlreadyFinished(val session: WorkoutSession) : FinishResult

    /** No sets were logged. Offer to discard instead of saving an empty session. */
    data object NothingLogged : FinishResult
    data object NotFound : FinishResult
}

sealed interface InSessionChange {
    data class Replaced(val exerciseName: String) : InSessionChange
    data class Skipped(val explanation: String) : InSessionChange

    /** The exercise already has logged sets; replacing it would mix two exercises in one entry. */
    data class AlreadyStarted(val explanation: String) : InSessionChange
    data object NotFound : InSessionChange
}

class SessionService(
    private val repos: Repositories,
    private val planning: PlanningService,
    private val clock: AppClock,
    private val ids: IdGenerator = IdGenerator.Random,
    private val events: ProductEvents = ProductEvents.None,
) {
    val activeState: Flow<ActiveSessionState?> = repos.sessions.activeState

    fun observe(sessionId: String): Flow<WorkoutSession?> = repos.sessions.observe(sessionId)

    suspend fun get(sessionId: String): WorkoutSession? = repos.sessions.get(sessionId)

    suspend fun inProgress(): WorkoutSession? = repos.sessions.all().firstOrNull { it.status == SessionStatus.IN_PROGRESS }

    // ------------------------------------------------------------------------------------ start

    suspend fun start(key: PlanKey): StartResult {
        inProgress()?.let { return StartResult.AlreadyActive(it.id) }
        val s = planning.snapshot()
        val (template, _) = planning.templateFor(s, key) ?: return StartResult.NothingToDo("This session is no longer available.")
        if (key is PlanKey.Template && !s.access.canUse(planning.accessFor(template))) return StartResult.NeedsPro
        val generated = planning.generate(s, key, planning.adjustments(key))
            ?: return StartResult.NothingToDo("This session is no longer available.")
        return startPlan(generated.plan, firstEver = s.finished.isEmpty())
    }

    private suspend fun startPlan(plan: WorkoutPlan, firstEver: Boolean): StartResult {
        if (plan.items.isEmpty()) {
            return StartResult.NothingToDo("Nothing in this session fits your current equipment and exclusions.")
        }
        val now = clock.nowMillis()
        val instant = Instant.ofEpochMilli(now).atZone(clock.zone())
        val session = WorkoutSession(
            id = ids.newId(),
            plan = plan,
            status = SessionStatus.IN_PROGRESS,
            startedAt = now,
            zoneId = clock.zone().id,
            localDate = instant.toLocalDate().toString(),
        )
        repos.transactions.run {
            repos.sessions.insert(session)
            repos.sessions.saveActiveState(PlayerMachine.initial(session, now))
        }
        if (firstEver) events.track(ProductEvent.FIRST_WORKOUT_STARTED)
        return StartResult.Started(session.id)
    }

    // ------------------------------------------------------------------------------------ player

    /** Loads the active state for [sessionId], recreating it if it was lost. Timers are re-derived from timestamps. */
    suspend fun loadPlayer(sessionId: String): Pair<WorkoutSession, ActiveSessionState>? {
        val session = repos.sessions.get(sessionId) ?: return null
        if (session.isFinished) return null
        val now = clock.nowMillis()
        val stored = repos.sessions.currentActiveState()?.takeIf { it.sessionId == sessionId }
        val state = PlayerMachine.normalize(stored ?: PlayerMachine.initial(session, now), session, now)
        if (stored != state) repos.sessions.saveActiveState(state)
        return session to state
    }

    suspend fun resumed() = events.track(ProductEvent.SESSION_RESUMED)

    suspend fun dispatch(sessionId: String, action: PlayerAction): Pair<WorkoutSession, ActiveSessionState>? =
        repos.transactions.run {
            val session = repos.sessions.get(sessionId)?.takeIf { !it.isFinished } ?: return@run null
            val now = clock.nowMillis()
            val state = repos.sessions.currentActiveState()?.takeIf { it.sessionId == sessionId }
                ?: PlayerMachine.initial(session, now)
            val transition = PlayerMachine.reduce(state, session, action, now)
            transition.upsertSets.forEach { repos.sessions.upsertSet(it) }
            transition.deleteSet?.let { (key, number) -> repos.sessions.deleteSet(sessionId, key, number) }
            transition.skipped?.let { repos.sessions.setSkipped(sessionId, it) }
            repos.sessions.saveActiveState(transition.state)
            (repos.sessions.get(sessionId) ?: session) to transition.state
        }

    // ------------------------------------------------------------------------------------ in-session changes

    suspend fun replacementOptions(sessionId: String, itemKey: String): ReplacementResult? {
        val session = repos.sessions.get(sessionId) ?: return null
        val item = session.plan.item(itemKey) ?: return null
        val s = planning.snapshot()
        val others = session.plan.items.filter { it.key != itemKey }.map { it.exerciseId }.toSet()
        return planning.generator.substitutions.replacements(item, others, constraintsFor(session, s))
    }

    suspend fun easierOption(sessionId: String, itemKey: String): EasierOption? {
        val session = repos.sessions.get(sessionId) ?: return null
        val item = session.plan.item(itemKey) ?: return null
        val s = planning.snapshot()
        val others = session.plan.items.filter { it.key != itemKey }.map { it.exerciseId }.toSet()
        return planning.generator.substitutions.easierOption(item, others, constraintsFor(session, s), equipmentFor(session, s), s.history)
    }

    /** Replaces an exercise for this workout only. Permanent exclusions are not touched. */
    suspend fun replace(sessionId: String, itemKey: String, exerciseId: ExerciseId): InSessionChange {
        val session = repos.sessions.get(sessionId) ?: return InSessionChange.NotFound
        val item = session.plan.item(itemKey) ?: return InSessionChange.NotFound
        if (session.setsFor(itemKey).isNotEmpty()) {
            return InSessionChange.AlreadyStarted(
                "You've already logged sets of ${item.exerciseName}. You can skip its remaining sets instead.",
            )
        }
        val exercise = planning.catalog.exercise(exerciseId) ?: return InSessionChange.NotFound
        val s = planning.snapshot()
        if (!constraintsFor(session, s).allows(exercise)) return InSessionChange.NotFound
        val updated = planning.generator.substitutions.applyReplacement(item, exercise, equipmentFor(session, s), s.history)
        repos.sessions.updatePlan(sessionId, session.plan.copy(items = session.plan.items.map { if (it.key == itemKey) updated else it }))
        events.track(ProductEvent.EXERCISE_REPLACED)
        return InSessionChange.Replaced(exercise.name)
    }

    suspend fun makeEasier(sessionId: String, itemKey: String): InSessionChange {
        val session = repos.sessions.get(sessionId) ?: return InSessionChange.NotFound
        val item = session.plan.item(itemKey) ?: return InSessionChange.NotFound
        val option = easierOption(sessionId, itemKey) ?: return InSessionChange.NotFound
        if (option is EasierOption.None) return InSessionChange.Skipped(option.description)
        if (option is EasierOption.Variation && session.setsFor(itemKey).isNotEmpty()) {
            return InSessionChange.AlreadyStarted(
                "You've already logged sets of ${item.exerciseName}. Lower the reps or weight for the remaining sets instead.",
            )
        }
        val s = planning.snapshot()
        val updated = planning.generator.substitutions.applyEasier(item, option, equipmentFor(session, s), s.history)
        repos.sessions.updatePlan(sessionId, session.plan.copy(items = session.plan.items.map { if (it.key == itemKey) updated else it }))
        // Keep the player's prefilled weight in step with a lighter prescription.
        if (option is EasierOption.Lighter) {
            repos.sessions.currentActiveState()?.takeIf { it.sessionId == sessionId }?.let {
                repos.sessions.saveActiveState(it.copy(draftLoad = option.load))
            }
        }
        return InSessionChange.Replaced(updated.exerciseName)
    }

    /**
     * Permanently excludes the exercise, then replaces it in this workout with the first suitable
     * option, or skips it with an explanation when nothing fits.
     */
    suspend fun excludeAndReplace(sessionId: String, itemKey: String): InSessionChange {
        val session = repos.sessions.get(sessionId) ?: return InSessionChange.NotFound
        val item = session.plan.item(itemKey) ?: return InSessionChange.NotFound
        planning.exclude(item.exerciseId)
        if (session.setsFor(itemKey).isNotEmpty()) {
            dispatchSkip(sessionId, itemKey)
            return InSessionChange.Skipped("${item.exerciseName} is excluded from future workouts. Its remaining sets are skipped today.")
        }
        return when (val options = replacementOptions(sessionId, itemKey)) {
            is ReplacementResult.Available -> replace(sessionId, itemKey, options.options.first().exercise.id)
            is ReplacementResult.None, null -> {
                dispatchSkip(sessionId, itemKey)
                InSessionChange.Skipped(
                    "${item.exerciseName} is excluded from future workouts. Nothing else fits today, so it's skipped. " +
                        (options as? ReplacementResult.None)?.explanation.orEmpty(),
                )
            }
        }
    }

    private suspend fun dispatchSkip(sessionId: String, itemKey: String) {
        val session = repos.sessions.get(sessionId) ?: return
        val state = repos.sessions.currentActiveState()?.takeIf { it.sessionId == sessionId }
        val index = session.plan.items.indexOfFirst { it.key == itemKey }
        if (state != null && state.itemIndex == index) {
            dispatch(sessionId, PlayerAction.SkipExercise)
        } else {
            repos.sessions.setSkipped(sessionId, session.skippedItemKeys + itemKey)
        }
    }

    private fun equipmentFor(session: WorkoutSession, s: PlanningSnapshot) =
        s.equipment.firstOrNull { it.id == session.plan.equipmentProfileId } ?: planning.activeEquipment(s)

    private fun constraintsFor(session: WorkoutSession, s: PlanningSnapshot) =
        planning.generator.constraintsFor(s.user.profile, equipmentFor(session, s), s.exclusions, session.plan.adjustments.quiet)

    // ------------------------------------------------------------------------------------ finishing

    /** Records completion exactly once. Repeated calls return [FinishResult.AlreadyFinished]. */
    suspend fun finish(sessionId: String): FinishResult {
        val result = repos.transactions.run {
            val session = repos.sessions.get(sessionId) ?: return@run FinishResult.NotFound
            if (session.isFinished) return@run FinishResult.AlreadyFinished(session)
            if (session.sets.isEmpty()) return@run FinishResult.NothingLogged
            val now = clock.nowMillis()
            val state = repos.sessions.currentActiveState()?.takeIf { it.sessionId == sessionId }
            val activeSeconds = ((state?.activeMs(now) ?: (now - session.startedAt).coerceAtLeast(0)) / 1000).toInt()
            val status = PlayerMachine.statusFor(session)
            // The free sample counts adaptive sessions where at least half of the main work was done.
            val counted = session.plan.adaptive && !planning.snapshot().access.isPro &&
                mainFraction(session) >= TrainingRules.ROTATION_ADVANCE_MAIN_FRACTION
            val changed = repos.sessions.finish(sessionId, status, now, activeSeconds, counted)
            if (!changed) return@run FinishResult.AlreadyFinished(session)
            repos.sessions.clearActiveState()

            val easedBack = session.plan.notes.any { it.kind == NoteKind.RETURN }
            repos.userState.update { u ->
                u.copy(
                    sampleSessionsUsed = if (counted) u.sampleSessionsUsed + 1 else u.sampleSessionsUsed,
                    returnPlan = if (easedBack) u.returnPlan?.let { it.copy(sessionsRemaining = (it.sessionsRemaining - 1).coerceAtLeast(0)) } else u.returnPlan,
                )
            }
            advanceRotation(session)
            if (repos.sessions.all().count { it.isFinished } == 1) {
                events.track(ProductEvent.FIRST_WORKOUT_COMPLETED)
            }
            FinishResult.Finished(repos.sessions.get(sessionId)!!)
        }
        return result
    }

    /** A program session moves the rotation on only if enough of its main work was done. */
    private suspend fun advanceRotation(session: WorkoutSession) {
        val source = session.plan.source as? PlanSource.Program ?: return
        val enrollment = repos.programs.currentEnrollment()
        val enrolledId = enrollment?.programId ?: app.forma.core.engine.catalog.Foundations.id
        if (enrolledId != source.programId && enrollment != null) return
        val index = enrollment?.rotationIndex ?: 0
        if (index != source.rotationIndex) return
        if (mainFraction(session) < TrainingRules.ROTATION_ADVANCE_MAIN_FRACTION) return
        repos.programs.setEnrollment(
            (enrollment ?: app.forma.core.model.ProgramEnrollment(source.programId, clock.nowMillis(), 0)).copy(rotationIndex = index + 1),
        )
    }

    /** Fraction of planned main-work sets that were logged. */
    private fun mainFraction(session: WorkoutSession): Double {
        val mains = session.plan.items.filter { it.phase == Phase.MAIN }
        val planned = mains.sumOf { it.sets }
        if (planned == 0) return 0.0
        val done = mains.sumOf { item -> session.setsFor(item.key).size.coerceAtMost(item.sets) }
        return done.toDouble() / planned
    }

    suspend fun discard(sessionId: String) {
        repos.transactions.run {
            val session = repos.sessions.get(sessionId) ?: return@run
            if (session.isFinished) return@run
            repos.sessions.delete(sessionId)
            if (repos.sessions.currentActiveState()?.sessionId == sessionId) repos.sessions.clearActiveState()
        }
    }

    // ------------------------------------------------------------------------------------ corrections

    suspend fun saveFeedback(sessionId: String, difficulty: Difficulty?, note: String) {
        repos.sessions.saveFeedback(sessionId, SessionFeedback(difficulty, note.trim().take(MAX_NOTE_LENGTH), clock.nowMillis()))
    }

    /** Edits or adds a logged set on a finished session, then re-evaluates complete vs partial. */
    suspend fun editSet(sessionId: String, itemKey: String, setNumber: Int, reps: Int?, seconds: Int?, load: Load?) {
        repos.transactions.run {
            val session = repos.sessions.get(sessionId) ?: return@run
            val item = session.plan.item(itemKey) ?: return@run
            val existing = session.setsFor(itemKey).firstOrNull { it.setNumber == setNumber }
            repos.sessions.upsertSet(
                SetLog(
                    sessionId = sessionId,
                    itemKey = itemKey,
                    setNumber = setNumber,
                    exerciseId = item.exerciseId,
                    exerciseName = item.exerciseName,
                    reps = reps,
                    seconds = seconds,
                    load = load,
                    completedAt = existing?.completedAt ?: (session.endedAt ?: clock.nowMillis()),
                ),
            )
            refreshStatus(sessionId)
        }
    }

    suspend fun deleteSet(sessionId: String, itemKey: String, setNumber: Int) {
        repos.transactions.run {
            repos.sessions.deleteSet(sessionId, itemKey, setNumber)
            refreshStatus(sessionId)
        }
    }

    private suspend fun refreshStatus(sessionId: String) {
        val session = repos.sessions.get(sessionId) ?: return
        if (!session.isFinished) return
        val status = PlayerMachine.statusFor(session)
        if (status != session.status) repos.sessions.updateStatus(sessionId, status)
    }

    /** Removes a session recorded by mistake. The free-sample counter is not refunded. */
    suspend fun deleteSession(sessionId: String) {
        repos.transactions.run {
            repos.sessions.delete(sessionId)
            if (repos.sessions.currentActiveState()?.sessionId == sessionId) repos.sessions.clearActiveState()
        }
    }

    companion object {
        const val MAX_NOTE_LENGTH = 1000
    }
}
