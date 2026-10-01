package app.forma.core.domain

import app.forma.core.model.Difficulty
import app.forma.core.model.Load
import app.forma.core.model.NoteKind
import app.forma.core.model.PlanSource
import app.forma.core.model.ProFeature
import app.forma.core.model.SessionStatus
import app.forma.core.model.Tier
import app.forma.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Acceptance criteria 14, 15 and 16. */
class AccessAndBackupTest {

    @Test
    fun `free users get three adaptive sessions, then progression is gated but nothing is locked away`() = runTest {
        val app = TestApp()
        app.onboard()
        repeat(3) {
            val id = app.doWholeSession()
            app.sessions.finish(id)
            app.clock.advanceDays(2)
        }
        val today = app.planning.observeToday().first()
        assertFalse(today.access.adaptive)
        assertTrue(today.upgradeOfferDue)
        val plan = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        assertFalse(plan.adaptive)
        assertTrue(plan.notes.any { it.kind == NoteKind.PRO_LIMIT })
        // Free users can still train, log and see all of their history.
        val id = app.doWholeSession()
        assertIs<FinishResult.Finished>(app.sessions.finish(id))
        assertEquals(4, app.repos.sessions.all().size)
        assertEquals(3, app.repos.userState.current().sampleSessionsUsed)
    }

    @Test
    fun `pro content is gated for free users`() = runTest {
        val app = TestApp()
        app.onboard()
        assertEquals(StartResult.NeedsPro, app.sessions.start(PlanKey.Template("dumbbell_express")))
        assertEquals(EnrollResult.NeedsPro, app.planning.enroll("steady_habit"))
        assertFalse(AccessState(app.billing.entitlement.value, 0).can(ProFeature.MULTIPLE_EQUIPMENT_PROFILES))
        assertIs<StartResult.Started>(app.sessions.start(PlanKey.Template("mobility_reset")))
    }

    @Test
    fun `an expiring entitlement never interrupts an active workout and keeps history`() = runTest {
        val app = TestApp()
        app.billing.setTier(Tier.PRO)
        app.onboard()
        assertIs<EnrollResult.Enrolled>(app.planning.enroll("dumbbell_strength"))
        val id = (app.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        app.sessions.dispatch(id, PlayerAction.CompleteSet())

        app.billing.setTier(Tier.FREE)
        app.logAllSets(id)
        val finished = assertIs<FinishResult.Finished>(app.sessions.finish(id)).session
        assertEquals(SessionStatus.COMPLETED, finished.status)
        assertEquals("dumbbell_strength", (finished.plan.source as PlanSource.Program).programId)

        // Future planning falls back to the free program with an explanation; history is untouched.
        val next = app.planning.currentPlan(PlanKey.ProgramNext)!!.plan
        assertEquals("foundations", (next.source as PlanSource.Program).programId)
        assertTrue(next.notes.any { it.kind == NoteKind.PRO_LIMIT && it.text.contains("Dumbbell Strength") })
        assertEquals(1, app.repos.sessions.all().size)
    }

    @Test
    fun `failed and cancelled purchases leave the person on free without errors`() = runTest {
        val app = TestApp()
        app.billing.nextPurchaseResult = PurchaseResult.Cancelled
        assertEquals(PurchaseResult.Cancelled, app.billing.purchase("annual"))
        assertFalse(app.billing.entitlement.value.isPro)
        app.billing.nextPurchaseResult = PurchaseResult.Failed("Network error")
        assertIs<PurchaseResult.Failed>(app.billing.purchase("monthly"))
        assertFalse(app.billing.entitlement.value.isPro)
        app.billing.nextPurchaseResult = PurchaseResult.Pending
        assertEquals(PurchaseResult.Pending, app.billing.purchase("monthly"))
        assertFalse(app.billing.entitlement.value.isPro)
        assertTrue(app.billing.entitlement.value.pendingPurchase)
        assertEquals(RestoreResult.NothingToRestore, app.billing.restore())
    }

    @Test
    fun `export and import round-trip all supported data`() = runTest {
        val app = TestApp()
        app.onboard()
        app.planning.exclude("wall_sit")
        app.planning.setFavorite("quiet_twenty", true)
        val id = app.doWholeSession()
        app.sessions.finish(id)
        app.sessions.saveFeedback(id, Difficulty.JUST_RIGHT, "Good, steady session")
        app.sessions.editSet(id, "squat", 1, reps = 9, seconds = null, load = Load.lb(15.0))
        app.planning.moveSession(app.clock.today().plusDays(2), app.clock.today().plusDays(3))
        val exported = app.backup.export()

        val other = TestApp()
        val result = other.backup.import(exported)
        assertEquals(ImportResult.Imported(1), result)
        assertEquals(app.repos.sessions.all(), other.repos.sessions.all())
        assertEquals(app.repos.userState.current(), other.repos.userState.current())
        assertEquals(app.repos.equipment.all(), other.repos.equipment.all())
        assertEquals(app.repos.exclusions.all(), other.repos.exclusions.all())
        assertEquals(app.repos.favorites.all(), other.repos.favorites.all())
        assertEquals(app.repos.programs.currentEnrollment(), other.repos.programs.currentEnrollment())
        assertEquals(app.repos.programs.allMoves(), other.repos.programs.allMoves())
        // A mixed-unit log survives byte-for-byte.
        assertEquals(Load(1500, WeightUnit.LB), other.repos.sessions.get(id)!!.setsFor("squat").first().load)
        // Exporting again produces the same content.
        assertEquals(exported.substringAfter("\"user\""), other.backup.export().substringAfter("\"user\""))
    }

    @Test
    fun `importing cannot reset the free sample and rejects other files`() = runTest {
        val app = TestApp()
        app.onboard()
        val emptyExport = app.backup.export()
        repeat(2) { app.sessions.finish(app.doWholeSession()); app.clock.advanceDays(2) }
        app.backup.import(emptyExport)
        assertEquals(2, app.repos.userState.current().sampleSessionsUsed)
        assertIs<ImportResult.Invalid>(app.backup.import("{\"hello\": 1}"))
        assertIs<ImportResult.Invalid>(app.backup.import("not json"))
        assertIs<ImportResult.Invalid>(app.backup.import(emptyExport.replace("\"version\": 1", "\"version\": 99")))
    }

    @Test
    fun `csv export lists each logged set with its own unit`() = runTest {
        val app = TestApp()
        app.onboard()
        val id = app.doWholeSession()
        app.sessions.finish(id)
        val csv = app.backup.exportSetsCsv()
        val lines = csv.trim().lines()
        assertEquals("date,session,exercise,set,reps,seconds,load,unit,status", lines.first())
        assertEquals(app.repos.sessions.get(id)!!.sets.size, lines.size - 1)
        assertTrue(lines.any { it.contains(",kg,") })
    }

    @Test
    fun `deleting local data removes everything`() = runTest {
        val app = TestApp()
        app.onboard()
        app.sessions.finish(app.doWholeSession())
        app.planning.exclude("wall_sit")
        app.reset.deleteAll()
        assertTrue(app.repos.sessions.all().isEmpty())
        assertTrue(app.repos.exclusions.all().isEmpty())
        assertTrue(app.repos.equipment.all().isEmpty())
        assertFalse(app.repos.userState.current().onboardingComplete)
    }
}
