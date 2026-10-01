package app.forma.harness

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.forma.core.domain.PlanKey
import app.forma.core.domain.StartResult
import app.forma.core.model.Difficulty
import app.forma.core.model.SessionStatus
import app.forma.presentation.Destination
import app.forma.presentation.Tab
import app.forma.ui.platform.HarnessSignals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives the real shared UI with clicks, on the JVM, against in-memory data. */
@OptIn(ExperimentalTestApi::class)
class FlowUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun reset() = HarnessSignals.reset()

    private fun setApp(env: HarnessEnv, start: Destination, device: Device = Device.Phone): HarnessNavigator {
        val navigator = HarnessNavigator(start)
        rule.setContent {
            // The desktop test window is 1024×768 px. A density below 768/891 keeps the whole phone
            // viewport (in dp) inside the window so every tap lands on screen; dp layout is unchanged.
            CompositionLocalProvider(LocalDensity provides Density(0.85f, device.fontScale)) {
                Box(Modifier.size(device.widthDp.dp, device.heightDp.dp)) {
                    CompositionLocalProvider(app.forma.ui.app.LocalLiveTimers provides false) {
                        HarnessTheme(dark = true, device = device) { HarnessApp(env, navigator) }
                    }
                }
            }
        }
        return navigator
    }

    private fun ComposeContentTestRule.waitForText(text: String, timeout: Long = 5_000) {
        try {
            waitUntil(timeout) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            dumpTree("waiting for \"$text\"")
            throw e
        }
    }

    private fun ComposeContentTestRule.dumpTree(reason: String) {
        println("---- semantics tree while $reason ----")
        val roots = onAllNodes(androidx.compose.ui.test.isRoot())
        for (i in 0 until roots.fetchSemanticsNodes().size) println(roots[i].printToString())
    }

    private fun ComposeContentTestRule.click(text: String): SemanticsNodeInteraction {
        waitForText(text)
        val node = onAllNodesWithText(text)[0]
        // Pinned actions (sticky buttons, sheets) are not inside a scrolling container.
        runCatching { node.performScrollTo() }
        return node.performClick()
    }

    @Test
    fun firstVerticalSlice_onboardingTodayPreviewPlayerCompletionHistory() {
        val env = HarnessEnv()
        val navigator = setApp(env, Destination.Onboarding)

        rule.click("Get started")
        rule.waitForText("What should we call you?")
        rule.onNodeWithContentDescription("Your name").performTextInput("Sam")
        rule.click("Continue")
        rule.click("Build strength")
        rule.click("Continue")
        rule.click("Some experience")
        rule.click("Continue")
        rule.click("Dumbbells")
        rule.click("I have a bench")
        rule.click("Continue")
        rule.click("30 min")
        rule.click("Continue")
        rule.waitForText("Which days usually suit you?")
        rule.click("Continue")
        rule.waitForText("Anything about your space?")
        rule.click("Continue")
        rule.click("I understand")
        rule.waitForText("Your first workout")
        rule.waitForText("Full body A")
        rule.waitForText("Goblet squat")
        rule.click("Go to Today")

        rule.waitForText("Good morning, Sam")
        assertEquals(Destination.Main(Tab.TODAY), navigator.current)
        assertTrue(runBlocking { env.services.repos.userState.current().onboardingComplete })

        // Preview, then start.
        rule.click("Exercises")
        rule.waitForText("Why this plan")
        rule.waitForText("WARM-UP")
        rule.click("Start workout")
        rule.waitForText("March in place")
        assertTrue(navigator.current is Destination.Player)
        assertTrue(HarnessSignals.keepScreenOn, "Screen stays on during an active workout")

        // Perform every set through the UI, waiting for each screen state explicitly.
        val labels = listOf("Skip rest", "Complete set", "Mark set as done", "Every set is logged")
        fun visible(label: String) = rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        fun loggedSets() = runBlocking { env.services.repos.sessions.all().first().sets.size }
        var guard = 0
        while (guard++ < 120) {
            try {
                rule.waitUntil(5_000) { labels.any(::visible) }
            } catch (e: Throwable) {
                rule.dumpTree("waiting for a player action (iteration $guard)")
                throw e
            }
            when {
                visible("Every set is logged") -> break
                visible("Skip rest") -> {
                    rule.onAllNodesWithText("Skip rest")[0].performClick()
                    rule.waitUntil(5_000) { !visible("Skip rest") }
                }
                else -> {
                    val before = loggedSets()
                    val label = if (visible("Complete set")) "Complete set" else "Mark set as done"
                    rule.onAllNodesWithText(label)[0].performClick()
                    rule.waitUntil(5_000) { loggedSets() == before + 1 }
                }
            }
        }
        rule.click("Finish workout")
        rule.waitForText("Session complete")
        assertFalse(HarnessSignals.keepScreenOn, "Keep-screen-on is released when the workout ends")
        val sessionId = (navigator.current as Destination.Summary).sessionId
        val session = runBlocking { env.services.repos.sessions.get(sessionId)!! }
        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(session.plannedSetCount, session.sets.size)

        rule.click("Just right")
        rule.waitUntil(5_000) { runBlocking { env.services.repos.sessions.get(sessionId)!!.feedback?.difficulty } == Difficulty.JUST_RIGHT }
        rule.click("Done")
        rule.waitForText("Done for today")

        rule.onNodeWithText("Progress").performClick()
        rule.waitForText("Weekly consistency")
        rule.waitForText("Sessions completed")
        rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(hasText("Full body A"))
        rule.onAllNodesWithText("Full body A")[0].assertIsDisplayed()
    }

    @Test
    fun largeTextOnASmallPhoneKeepsPrimaryActionsReachable() {
        val env = HarnessEnv().also { Seed.onboard(it) }
        val navigator = setApp(env, Destination.Main(Tab.TODAY), Device.SmallHugeText)
        rule.waitForText("Today")
        rule.onAllNodesWithText("Start workout")[0].performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        listOf("Explore", "Progress", "You").forEach { rule.onNode(hasText(it)).assertIsDisplayed() }

        val id = runBlocking { (env.services.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId }
        rule.runOnIdle { navigator.navigate(Destination.Player(id)) }
        rule.waitForText("March in place")
        // The primary action is pinned and visible without scrolling.
        rule.onAllNodesWithText("Mark set as done")[0].assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        rule.onNodeWithText("Pause").assertIsDisplayed()
        rule.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun backDuringAWorkoutOpensTheMenuInsteadOfLeaving() {
        val env = HarnessEnv().also { Seed.onboard(it) }
        val id = runBlocking { (env.services.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId }
        val navigator = setApp(env, Destination.Player(id))
        rule.waitForText("March in place")
        rule.runOnIdle { HarnessSignals.pressBack() }
        rule.waitForText("Save and exit")
        rule.mainClock.advanceTimeBy(1_000) // let the sheet finish sliding in
        assertTrue(navigator.current is Destination.Player)
        // The sheet's "Pause" row is the last match; the player's own Pause button sits underneath.
        rule.onAllNodesWithText("Pause").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForText("Paused")
        rule.waitUntil(5_000) { !HarnessSignals.keepScreenOn }
        assertTrue(runBlocking { env.services.repos.sessions.currentActiveState()!!.paused })
    }

    @Test
    fun adjustTodayPreviewsAndAppliesAShorterSession() {
        val env = HarnessEnv().also { Seed.onboard(it, minutes = 45) }
        setApp(env, Destination.Main(Tab.TODAY))
        rule.click("Adjust today")
        rule.waitForText("TIME AVAILABLE")
        rule.click("15 min")
        rule.waitForText("Shortened to about")
        rule.click("Apply to today")
        rule.waitUntil(5_000) { runBlocking { env.services.planning.adjustments(PlanKey.ProgramNext).targetMinutes } == 15 }
        rule.waitForText("Adjusted")
    }

    @Test
    fun excludingFromThePreviewRemovesTheExerciseFromFutureWorkouts() {
        val env = HarnessEnv().also { Seed.onboard(it) }
        setApp(env, Destination.Preview(PlanKey.ProgramNext.value))
        rule.click("Goblet squat")
        rule.click("Exclude permanently")
        rule.waitForText("Exclude Goblet squat?")
        rule.onNode(hasText("Exclude") and androidx.compose.ui.test.hasClickAction()).performClick()
        rule.waitUntil(5_000) { runBlocking { env.services.repos.exclusions.all().map { it.exerciseId } } == listOf("goblet_squat") }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Goblet squat").fetchSemanticsNodes().isEmpty() }
    }
}
