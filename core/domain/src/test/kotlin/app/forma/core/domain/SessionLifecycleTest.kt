package app.forma.core.domain

import app.forma.core.model.Difficulty
import app.forma.core.model.Load
import app.forma.core.model.PlayerPhase
import app.forma.core.model.SessionStatus
import app.forma.core.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

/** Acceptance criteria 8, 9 and 10, plus editing logged sets. */
class SessionLifecycleTest {

    private suspend fun started(app: TestApp): String {
        app.onboard()
        return (app.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
    }

    @Test
    fun `rest timers are derived from stored timestamps and survive process death`() = runTest {
        val app = TestApp()
        val id = started(app)
        val (session, _) = app.sessions.dispatch(id, PlayerAction.CompleteSet())!!
        val rest = session.plan.items[0].restSeconds

        // Process death 4 seconds into the rest.
        app.clock.advanceSeconds(4)
        val revived = app.recreate()
        val (_, state) = revived.sessions.loadPlayer(id)!!
        assertEquals(PlayerPhase.REST, state.phase)
        assertEquals((rest - 4) * 1000L, state.restTimer!!.remainingMs(app.clock.nowMillis()))

        // The rest finishes while the app is in the background.
        app.clock.advanceSeconds(rest.toLong())
        val (_, later) = revived.sessions.loadPlayer(id)!!
        assertEquals(PlayerPhase.WORK, later.phase)
        assertEquals(1, revived.sessions.get(id)!!.sets.size)
    }

    @Test
    fun `pausing freezes timers and active time, resuming continues them`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.clock.advanceSeconds(30)
        app.sessions.dispatch(id, PlayerAction.CompleteSet())
        app.sessions.dispatch(id, PlayerAction.AddRest(15))
        app.clock.advanceSeconds(3)
        app.sessions.dispatch(id, PlayerAction.Pause)
        val pausedRemaining = app.repos.sessions.currentActiveState()!!.restTimer!!.remainingMs(app.clock.nowMillis())
        app.clock.advanceSeconds(120) // a phone call
        val (_, paused) = app.recreate().sessions.loadPlayer(id)!!
        assertEquals(pausedRemaining, paused.restTimer!!.remainingMs(app.clock.nowMillis()))
        assertEquals(PlayerPhase.REST, paused.phase)

        val (_, resumed) = app.sessions.dispatch(id, PlayerAction.Resume)!!
        app.clock.advanceSeconds(2)
        assertEquals(pausedRemaining - 2000, resumed.restTimer!!.remainingMs(app.clock.nowMillis()))
        assertEquals(35_000, resumed.activeMs(app.clock.nowMillis()))
    }

    @Test
    fun `timed sets use the work timer and are only logged when confirmed`() = runTest {
        val app = TestApp()
        val id = started(app)
        val (session, state) = app.sessions.loadPlayer(id)!!
        val item = session.plan.items[state.itemIndex]
        assertIs<Target.Time>(item.target)
        app.sessions.dispatch(id, PlayerAction.StartWorkTimer)
        app.clock.advanceSeconds(120)
        // Time passing never logs a set by itself.
        assertTrue(app.sessions.loadPlayer(id)!!.first.sets.isEmpty())
        val (after, _) = app.sessions.dispatch(id, PlayerAction.CompleteSet())!!
        assertEquals(120, after.sets.single().seconds)
    }

    @Test
    fun `completion is recorded exactly once even with repeated taps`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.logAllSets(id)
        val results = (1..5).map { async { app.sessions.finish(id) } }.awaitAll()
        assertEquals(1, results.count { it is FinishResult.Finished })
        assertEquals(4, results.count { it is FinishResult.AlreadyFinished })
        assertEquals(1, app.repos.userState.current().sampleSessionsUsed)
        assertEquals(1, app.repos.programs.currentEnrollment()!!.rotationIndex)
        assertEquals(1, app.repos.sessions.all().size)
        assertNull(app.repos.sessions.currentActiveState())
        val session = app.repos.sessions.get(id)!!
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(session.plannedSetCount, session.sets.size)
    }

    @Test
    fun `logging the same set twice never duplicates it`() = runTest {
        val app = TestApp()
        val id = started(app)
        val set = app.sessions.dispatch(id, PlayerAction.CompleteSet())!!.first.sets.single()
        app.repos.sessions.upsertSet(set)
        app.repos.sessions.upsertSet(set.copy(reps = 3))
        assertEquals(1, app.repos.sessions.get(id)!!.sets.size)
    }

    @Test
    fun `skipped work is represented honestly as partial`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.sessions.dispatch(id, PlayerAction.CompleteSet())
        app.sessions.dispatch(id, PlayerAction.SkipRest)
        app.sessions.dispatch(id, PlayerAction.SkipExercise)
        val (session, state) = app.sessions.loadPlayer(id)!!
        assertEquals(setOf(session.plan.items[1].key), session.skippedItemKeys)
        assertEquals(2, state.itemIndex)
        val finished = (app.sessions.finish(id) as FinishResult.Finished).session
        assertEquals(SessionStatus.PARTIAL, finished.status)
        assertEquals(1, finished.sets.size)
        // A session with less than half the main work does not advance the program or use the sample.
        assertEquals(0, app.repos.programs.currentEnrollment()!!.rotationIndex)
        assertEquals(0, app.repos.userState.current().sampleSessionsUsed)
    }

    @Test
    fun `a session with nothing logged cannot be saved and can be discarded`() = runTest {
        val app = TestApp()
        val id = started(app)
        assertEquals(FinishResult.NothingLogged, app.sessions.finish(id))
        app.sessions.discard(id)
        assertNull(app.repos.sessions.get(id))
        assertNull(app.repos.sessions.currentActiveState())
        assertIs<StartResult.Started>(app.sessions.start(PlanKey.ProgramNext))
    }

    @Test
    fun `only one session can be in progress`() = runTest {
        val app = TestApp()
        val id = started(app)
        assertEquals(StartResult.AlreadyActive(id), app.sessions.start(PlanKey.Template("mobility_reset")))
    }

    @Test
    fun `undo removes the last logged set and returns to it`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.sessions.dispatch(id, PlayerAction.CompleteSet())
        val (session, state) = app.sessions.dispatch(id, PlayerAction.UndoLastSet)!!
        assertTrue(session.sets.isEmpty())
        assertEquals(0, state.itemIndex)
        assertEquals(1, state.setNumber)
    }

    @Test
    fun `logged sets can be corrected after finishing and status follows`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.logAllSets(id)
        app.sessions.finish(id)
        val session = app.repos.sessions.get(id)!!
        val squatSet = session.setsFor("squat").first()
        app.sessions.editSet(id, "squat", squatSet.setNumber, reps = 7, seconds = null, load = Load.kg(7.5))
        val edited = app.repos.sessions.get(id)!!.setsFor("squat").first()
        assertEquals(7, edited.reps)
        assertEquals(Load.kg(7.5), edited.load)
        app.sessions.deleteSet(id, "squat", squatSet.setNumber)
        assertEquals(SessionStatus.PARTIAL, app.repos.sessions.get(id)!!.status)
        app.sessions.saveFeedback(id, Difficulty.HARD, "  Felt heavy  ")
        assertEquals("Felt heavy", app.repos.sessions.get(id)!!.feedback!!.note)
        app.sessions.deleteSession(id)
        assertNull(app.repos.sessions.get(id))
    }

    @Test
    fun `history keeps the workout actually performed even after the plan changes`() = runTest {
        val app = TestApp()
        val id = started(app)
        app.logAllSets(id)
        app.sessions.finish(id)
        val snapshot = app.repos.sessions.get(id)!!.plan
        app.planning.exclude(snapshot.items.first { it.key == "squat" }.exerciseId)
        app.repos.userState.update { it.copy(profile = it.profile.copy(sessionMinutes = 10)) }
        assertEquals(snapshot, app.repos.sessions.get(id)!!.plan)
        assertNotNull(snapshot.items.first().cues.firstOrNull())
    }
}
