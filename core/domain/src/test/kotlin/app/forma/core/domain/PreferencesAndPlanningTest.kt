package app.forma.core.domain

import app.forma.core.engine.DayState
import app.forma.core.engine.ReplacementResult
import app.forma.core.engine.ReturnStatus
import app.forma.core.model.NoteKind
import app.forma.core.model.SessionStatus
import app.forma.core.model.Tier
import app.forma.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Acceptance criteria 1, 2, 6, 7 and 11 at the service level. */
class PreferencesAndPlanningTest {

    @Test
    fun `exclusions survive restarts, program changes and regeneration until restored`() = runTest {
        val app = TestApp()
        app.onboard()
        val first = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        val excluded = first.items.first { it.key == "squat" }.exerciseId
        app.planning.exclude(excluded)

        val restarted = app.recreate()
        assertTrue(restarted.planning.currentPlan(PlanKey.ProgramNext)!!.plan.items.none { it.exerciseId == excluded })
        app.billing.setTier(Tier.PRO)
        assertIs<EnrollResult.Enrolled>(restarted.planning.enroll("dumbbell_strength"))
        for (template in restarted.planning.catalog.programs.flatMap { it.rotation } + restarted.planning.catalog.standaloneSessions) {
            val plan = restarted.planning.currentPlan(PlanKey.Template(template.id))!!.plan
            assertTrue(plan.items.none { it.exerciseId == excluded }, template.id)
        }
        restarted.planning.restore(excluded)
        assertTrue(app.repos.exclusions.all().isEmpty())
    }

    @Test
    fun `a temporary swap never changes permanent preferences`() = runTest {
        val app = TestApp()
        app.onboard()
        val plan = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        val push = plan.items.first { it.key == "push" }
        val options = app.planning.replacementOptions(PlanKey.ProgramNext, "push") as ReplacementResult.Available
        val replacement = options.options.first().exercise
        app.planning.replaceForToday(PlanKey.ProgramNext, "push", replacement.id)

        assertTrue(app.repos.exclusions.all().isEmpty())
        val today = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        assertEquals(replacement.id, today.items.first { it.key == "push" }.exerciseId)

        // In-session replacement is also temporary.
        val id = (app.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        val squatOptions = app.sessions.replacementOptions(id, "squat") as ReplacementResult.Available
        app.sessions.replace(id, "squat", squatOptions.options.first().exercise.id)
        assertTrue(app.repos.exclusions.all().isEmpty())
        app.logAllSets(id)
        app.sessions.finish(id)

        // Tomorrow the original exercises come back.
        app.clock.advanceDays(2)
        val later = app.planning.currentPlan(PlanKey.Template("foundations_a"))!!.plan
        assertEquals(push.exerciseId, later.items.first { it.key == "push" }.exerciseId)
        assertEquals(plan.items.first { it.key == "squat" }.exerciseId, later.items.first { it.key == "squat" }.exerciseId)
    }

    @Test
    fun `exclude during a workout replaces the exercise and keeps it excluded`() = runTest {
        val app = TestApp()
        app.onboard()
        val id = (app.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        val before = app.sessions.get(id)!!.plan.items.first { it.key == "hinge" }.exerciseId
        val change = app.sessions.excludeAndReplace(id, "hinge")
        assertIs<InSessionChange.Replaced>(change)
        assertEquals(listOf(before), app.repos.exclusions.all().map { it.exerciseId })
        val after = app.sessions.get(id)!!.plan.items.first { it.key == "hinge" }.exerciseId
        assertTrue(after != before)
    }

    @Test
    fun `missed planned days are shown as not done and never counted`() = runTest {
        val app = TestApp() // 2026-09-28 is a Monday
        app.onboard(days = setOf(1, 3, 5))
        app.clock.advanceDays(4) // Friday, nothing done on Monday or Wednesday
        val today = app.planning.observeToday().first()
        assertEquals(0, today.week.completedCount)
        assertEquals(3, today.week.plannedCount)
        assertEquals(DayState.NOT_DONE, today.week.days[0].state)
        assertEquals(DayState.NOT_DONE, today.week.days[2].state)
        assertIs<TodayDay.Planned>(today.day)
        // No pile-up: today is a single normal session, the next one in the rotation.
        assertEquals("foundations_a", (today.day as TodayDay.Planned).plan.plan.source.sessionTemplateId)
    }

    @Test
    fun `returning after a long break offers a check-in and preserves all history`() = runTest {
        val app = TestApp()
        app.onboard()
        val first = app.doWholeSession()
        app.sessions.finish(first)
        app.clock.advanceDays(35)

        val today = app.planning.observeToday().first()
        assertIs<ReturnStatus.LongBreak>(today.returnStatus)
        app.planning.answerReturnCheckIn(easeBack = true)
        assertEquals(ReturnStatus.None, app.planning.observeToday().first().returnStatus)

        val plan = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        assertTrue(plan.notes.any { it.kind == NoteKind.RETURN })
        val id = app.doWholeSession()
        app.sessions.finish(id)
        assertEquals(1, app.repos.userState.current().returnPlan!!.sessionsRemaining)
        assertEquals(2, app.repos.sessions.all().count { it.isFinished })
        assertEquals(first, app.repos.sessions.all().last().id)
    }

    @Test
    fun `missing a single day does not trigger the return check-in`() = runTest {
        val app = TestApp()
        app.onboard()
        app.sessions.finish(app.doWholeSession())
        app.clock.advanceDays(3)
        assertEquals(ReturnStatus.None, app.planning.observeToday().first().returnStatus)
    }

    @Test
    fun `changing the display unit never rewrites stored performance`() = runTest {
        val app = TestApp()
        app.onboard()
        val id = app.doWholeSession()
        app.sessions.finish(id)
        val before = app.repos.sessions.get(id)!!.sets
        assertTrue(before.any { it.load?.unit == WeightUnit.KG })
        app.repos.userState.update { it.copy(settings = it.settings.copy(displayUnit = WeightUnit.LB)) }
        assertEquals(before, app.repos.sessions.get(id)!!.sets)
    }

    @Test
    fun `the person's usual time shapes program sessions`() = runTest {
        val app = TestApp()
        app.onboard(minutes = 15)
        val plan = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        assertTrue(plan.estimatedSeconds <= 15 * 60 * 1.1)
        assertTrue(plan.notes.any { it.kind == NoteKind.SHORTENED })
        assertTrue(plan.adjustments.isEmpty)
    }

    @Test
    fun `moving today's session plans it on another day without adding sessions`() = runTest {
        val app = TestApp()
        app.onboard(days = setOf(1, 3, 5))
        val tuesday = app.clock.today().plusDays(1)
        app.planning.moveSession(app.clock.today(), tuesday)
        val today = app.planning.observeToday().first()
        assertIs<TodayDay.Rest>(today.day)
        assertEquals(3, today.week.plannedCount)
        assertEquals(tuesday, today.week.days.single { it.planned && it.date.dayOfWeek.value == 2 }.date)
    }

    @Test
    fun `sessions finished as partial are counted as partial, not complete`() = runTest {
        val app = TestApp()
        app.onboard()
        val id = (app.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        app.sessions.dispatch(id, PlayerAction.CompleteSet())
        val result = app.sessions.finish(id) as FinishResult.Finished
        assertEquals(SessionStatus.PARTIAL, result.session.status)
        assertEquals(1, result.session.sets.size)
        val today = app.planning.observeToday().first()
        assertEquals(DayState.PARTIAL, today.week.days[0].state)
    }
}
