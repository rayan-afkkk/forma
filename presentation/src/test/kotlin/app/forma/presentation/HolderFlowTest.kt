package app.forma.presentation

import app.forma.core.domain.DevelopmentBilling
import app.forma.core.domain.FakeClock
import app.forma.core.domain.IdGenerator
import app.forma.core.domain.PurchaseResult
import app.forma.core.domain.Repositories
import app.forma.core.model.Difficulty
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.PlayerPhase
import app.forma.core.model.SessionStatus
import app.forma.core.model.ThemeMode
import app.forma.core.model.Tier
import app.forma.presentation.explore.ExploreFilter
import app.forma.presentation.explore.ExploreStateHolder
import app.forma.presentation.onboarding.OnboardingStateHolder
import app.forma.presentation.onboarding.OnboardingStep
import app.forma.presentation.player.PlayerSheet
import app.forma.presentation.player.PlayerStateHolder
import app.forma.presentation.progress.ProgressStateHolder
import app.forma.presentation.root.AppStateHolder
import app.forma.presentation.summary.SessionSummaryStateHolder
import app.forma.presentation.today.TodayCard
import app.forma.presentation.today.TodayStateHolder
import app.forma.presentation.you.MembershipStateHolder
import app.forma.presentation.you.YouStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HolderFlowTest {

    private class Env(val scope: CoroutineScope) {
        val clock = FakeClock()
        val billing = DevelopmentBilling(Tier.FREE, clock)
        private var n = 0
        val services = AppServices(Repositories.inMemory(), clock, billing, ids = IdGenerator { "s${++n}" })
        val effects = mutableListOf<Effect>()

        fun <T : StateHolder<*>> watch(holder: T): T {
            scope.launch { holder.effects.collect { effects += it } }
            return holder
        }
    }

    /**
     * Holders run on a scope driven by the test scheduler but outside the test's own job:
     * advanceUntilIdle() runs their work, and their never-ending collectors don't block completion.
     */
    private fun TestScope.env() = Env(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()))

    private suspend fun TestScope.onboard(env: Env, minutes: Int = 30) {
        val holder = env.watch(OnboardingStateHolder(env.scope, env.services))
        holder.next() // welcome -> name
        holder.setName("Sam Rivera")
        holder.next()
        holder.setGoal(Goal.STRENGTH)
        holder.next()
        holder.setExperience(ExperienceLevel.SOME)
        holder.next()
        holder.setEquipment(holder.state.value.equipment.copy(hasDumbbells = true, bench = true))
        holder.next()
        holder.setMinutes(minutes)
        holder.next()
        holder.next() // schedule (Mon/Wed/Fri)
        holder.next() // constraints
        holder.next() // safety -> preview
        advanceUntilIdle()
        val state = holder.state.value
        assertEquals(OnboardingStep.PREVIEW, state.step)
        assertNotNull(state.preview, state.previewError)
        holder.finish(startNow = false)
        advanceUntilIdle()
    }

    @Test
    fun `onboarding finishes with a real preview and lands on Today`() = runTest {
        val env = env()
        onboard(env)
        val user = env.services.repos.userState.current()
        assertTrue(user.onboardingComplete)
        assertEquals("Sam Rivera", user.profile.name)
        assertTrue(env.effects.any { it is Effect.Navigate && it.destination == Destination.Main(Tab.TODAY) && it.clearBackStack })
        val today = TodayStateHolder(env.scope, env.services)
        advanceUntilIdle()
        val card = assertIs<TodayCard.Planned>(today.state.value.card)
        assertEquals("Full body A", card.plan.title)
        assertTrue(today.state.value.greeting.endsWith("Sam Rivera"))
        assertEquals("0 of 3 planned sessions done this week", today.state.value.week!!.summary)
    }

    @Test
    fun `equipment step cannot continue with invalid dumbbells`() = runTest {
        val env = env()
        val holder = OnboardingStateHolder(env.scope, env.services)
        repeat(4) { holder.next(); holder.setGoal(Goal.GENERAL_FITNESS); holder.setExperience(ExperienceLevel.NEW) }
        assertEquals(OnboardingStep.EQUIPMENT, holder.state.value.step)
        holder.setEquipment(holder.state.value.equipment.copy(hasDumbbells = true, adjustableMin = "20", adjustableMax = "5"))
        assertFalse(holder.state.value.canContinue)
        holder.next()
        assertEquals(OnboardingStep.EQUIPMENT, holder.state.value.step)
    }

    @Test
    fun `adjust today previews changes before applying and explains impractical times`() = runTest {
        val env = env()
        onboard(env, minutes = 45)
        val today = env.watch(TodayStateHolder(env.scope, env.services))
        advanceUntilIdle()
        today.openAdjust()
        advanceUntilIdle()
        val full = today.state.value.adjust!!
        today.setMinutes(15)
        advanceUntilIdle()
        val short = today.state.value.adjust!!
        assertTrue(short.changed)
        assertTrue(short.previewNotes.any { it.startsWith("Shortened") }, short.previewNotes.toString())
        // Nothing is saved until applied.
        assertTrue(env.services.planning.adjustments(app.forma.core.domain.PlanKey.ProgramNext).isEmpty)
        today.setMinutes(5)
        advanceUntilIdle()
        assertNotNull(today.state.value.adjust!!.impractical)
        today.setMinutes(15)
        today.applyAdjust()
        advanceUntilIdle()
        assertEquals(15, env.services.planning.adjustments(app.forma.core.domain.PlanKey.ProgramNext).targetMinutes)
        assertNull(today.state.value.adjust)
        assertTrue(full.previewDuration != short.previewDuration)
    }

    @Test
    fun `the whole first workout runs from Today through the player to the summary and history`() = runTest {
        val env = env()
        onboard(env)
        val today = env.watch(TodayStateHolder(env.scope, env.services))
        advanceUntilIdle()
        today.start()
        advanceUntilIdle()
        val playerNav = env.effects.filterIsInstance<Effect.Navigate>().last()
        val sessionId = assertIs<Destination.Player>(playerNav.destination).sessionId

        val player = env.watch(PlayerStateHolder(env.scope, env.services, sessionId, autoTick = false))
        advanceUntilIdle()
        assertEquals("March in place", player.state.value.exerciseName)
        assertNotNull(player.state.value.timed)

        // A fast double tap logs one set, not two.
        player.completeSet()
        player.completeSet()
        advanceUntilIdle()
        assertEquals(1, env.services.repos.sessions.get(sessionId)!!.sets.size)
        assertEquals(PlayerPhase.REST, player.state.value.phase)
        assertNotNull(player.state.value.restRemaining)

        // Rest ends by time; the screen moves to work without logging anything.
        env.clock.advanceSeconds(60)
        player.tick()
        advanceUntilIdle()
        assertEquals(PlayerPhase.WORK, player.state.value.phase)
        assertEquals(1, env.services.repos.sessions.get(sessionId)!!.sets.size)
        assertTrue(env.effects.any { it is Effect.Cue })

        // Do every remaining set.
        var guard = 0
        while (player.state.value.phase != PlayerPhase.FINISHED && guard++ < 100) {
            if (player.state.value.phase == PlayerPhase.REST) player.skipRest()
            advanceUntilIdle()
            player.completeSet()
            advanceUntilIdle()
        }
        val session = env.services.repos.sessions.get(sessionId)!!
        assertEquals(session.plannedSetCount, session.sets.size)
        player.requestFinish()
        advanceUntilIdle()
        val summaryNav = env.effects.filterIsInstance<Effect.Navigate>().last()
        assertEquals(Destination.Summary(sessionId, justFinished = true), summaryNav.destination)
        assertTrue(summaryNav.replaceCurrent)

        val summary = SessionSummaryStateHolder(env.scope, env.services, sessionId, justFinished = true)
        advanceUntilIdle()
        assertEquals("Session complete", summary.state.value.heading)
        assertFalse(summary.state.value.partial)
        assertNotNull(summary.state.value.sampleNotice)
        summary.setDifficulty(Difficulty.JUST_RIGHT)
        advanceUntilIdle()
        assertEquals(Difficulty.JUST_RIGHT, env.services.repos.sessions.get(sessionId)!!.feedback!!.difficulty)

        val progress = ProgressStateHolder(env.scope, env.services)
        advanceUntilIdle()
        assertFalse(progress.state.value.empty)
        assertEquals(sessionId, progress.state.value.history.single().sessionId)
        assertTrue(progress.state.value.trendsLocked)

        advanceUntilIdle()
        assertIs<TodayCard.Done>(today.state.value.card)
    }

    @Test
    fun `finishing early asks first and saves an honest partial session`() = runTest {
        val env = env()
        onboard(env)
        val sessionId = (env.services.sessions.start(app.forma.core.domain.PlanKey.ProgramNext) as app.forma.core.domain.StartResult.Started).sessionId
        val player = env.watch(PlayerStateHolder(env.scope, env.services, sessionId, autoTick = false))
        advanceUntilIdle()
        player.completeSet()
        advanceUntilIdle()
        player.requestFinish()
        val sheet = assertIs<PlayerSheet.ConfirmFinish>(player.state.value.sheet)
        assertEquals(1, sheet.done)
        player.finish()
        advanceUntilIdle()
        assertEquals(SessionStatus.PARTIAL, env.services.repos.sessions.get(sessionId)!!.status)
    }

    @Test
    fun `back during a workout opens the menu, and save and exit keeps the session resumable`() = runTest {
        val env = env()
        onboard(env)
        val sessionId = (env.services.sessions.start(app.forma.core.domain.PlanKey.ProgramNext) as app.forma.core.domain.StartResult.Started).sessionId
        val player = env.watch(PlayerStateHolder(env.scope, env.services, sessionId, autoTick = false))
        advanceUntilIdle()
        player.onBack()
        assertEquals(PlayerSheet.Menu, player.state.value.sheet)
        player.saveAndExit()
        advanceUntilIdle()
        assertTrue(env.effects.last() is Effect.Back)
        val today = TodayStateHolder(env.scope, env.services)
        advanceUntilIdle()
        assertEquals(sessionId, today.state.value.resume?.sessionId)
        assertTrue(env.services.repos.sessions.currentActiveState()!!.paused)
    }

    @Test
    fun `explore search, filters and favorites work`() = runTest {
        val env = env()
        onboard(env)
        val explore = ExploreStateHolder(env.scope, env.services)
        advanceUntilIdle()
        val all = explore.state.value.results
        assertTrue(all.size >= 10)
        assertTrue(all.any { it.locked })
        explore.setQuery("mobility")
        advanceUntilIdle()
        assertTrue(explore.state.value.results.size < all.size)
        assertTrue(explore.state.value.results.any { it.templateId == "mobility_reset" })
        explore.clearFilters()
        explore.toggleFilter(ExploreFilter.SHORT)
        advanceUntilIdle()
        assertTrue(explore.state.value.results.isNotEmpty())
        assertTrue(explore.state.value.results.all { it.minutes <= 15 })
        explore.clearFilters()
        explore.toggleFavorite("quiet_twenty")
        advanceUntilIdle()
        explore.toggleFilter(ExploreFilter.FAVORITES)
        advanceUntilIdle()
        assertEquals(listOf("quiet_twenty"), explore.state.value.results.map { it.templateId })
    }

    @Test
    fun `purchase cancellation and failure leave the person on free without crashing`() = runTest {
        val env = env()
        onboard(env)
        val paywall = env.watch(MembershipStateHolder(env.scope, env.services))
        advanceUntilIdle()
        assertTrue(paywall.state.value.offers.isNotEmpty())
        assertTrue(paywall.state.value.offers.all { it.placeholder && it.terms.contains("Renews automatically") })
        env.billing.nextPurchaseResult = PurchaseResult.Cancelled
        paywall.purchase()
        advanceUntilIdle()
        assertFalse(paywall.state.value.isPro)
        assertFalse(paywall.state.value.purchasing)
        env.billing.nextPurchaseResult = PurchaseResult.Failed("Payment declined.")
        paywall.purchase()
        advanceUntilIdle()
        assertTrue(env.effects.any { it == Effect.Message("Payment declined.") })
        env.billing.nextPurchaseResult = PurchaseResult.Purchased
        paywall.purchase()
        advanceUntilIdle()
        assertTrue(paywall.state.value.isPro)
    }

    @Test
    fun `theme choice persists across app restarts`() = runTest {
        val env = env()
        onboard(env)
        val you = YouStateHolder(env.scope, env.services)
        advanceUntilIdle()
        you.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        val restarted = AppStateHolder(env.scope, AppServices(env.services.repos, env.clock, env.billing))
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, restarted.state.value.theme)
        assertTrue(restarted.state.value.onboarded)
    }

    @Test
    fun `progress shows an empty state before the first session`() = runTest {
        val env = env()
        onboard(env)
        val progress = ProgressStateHolder(env.scope, env.services)
        advanceUntilIdle()
        assertTrue(progress.state.value.empty)
    }
}
