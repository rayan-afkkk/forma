package app.forma.harness

import app.forma.core.domain.PlanKey
import app.forma.core.domain.PlayerAction
import app.forma.core.domain.StartResult
import app.forma.presentation.Destination
import app.forma.presentation.Tab
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * Renders real screens, driven by the real state holders and services with seeded local data,
 * to PNG files under docs/screenshots. These are JVM renderings with Compose for Desktop and the
 * app's bundled fonts, not device screenshots.
 */
class ScreenshotTest {
    private val root = File(System.getProperty("forma.screenshotDir") ?: "build/screenshots")

    private fun shot(name: String, env: HarnessEnv, destination: Destination, device: Device = Device.Phone, dark: Boolean = true) {
        val dir = File(root, if (dark) device.name else "${device.name}-light")
        snapshot(File(dir, "$name.png"), device, dark, frames = 4) {
            HarnessApp(env, HarnessNavigator(destination))
        }
    }

    private fun main(tab: Tab) = Destination.Main(tab)

    @Test
    fun mainScreens() {
        val env = HarnessEnv().also { Seed.history(it) }
        for (device in listOf(Device.Phone, Device.SmallPhone, Device.LargeText)) {
            shot("01-today", env, main(Tab.TODAY), device)
            shot("02-explore", env, main(Tab.EXPLORE), device)
            shot("03-progress", env, main(Tab.PROGRESS), device)
            shot("04-you", env, main(Tab.YOU), device)
            shot("05-preview", env, Destination.Preview(PlanKey.ProgramNext.value), device)
        }
        shot("01-today", env, main(Tab.TODAY), Device.Phone, dark = false)
        shot("02-explore", env, main(Tab.EXPLORE), Device.Phone, dark = false)
        shot("03-progress", env, main(Tab.PROGRESS), Device.Phone, dark = false)
        shot("04-you", env, main(Tab.YOU), Device.Phone, dark = false)
        shot("01-today", env, main(Tab.TODAY), Device.SmallHugeText)
    }

    @Test
    fun emptyAndRestStates() {
        val fresh = HarnessEnv().also { Seed.onboard(it) }
        shot("10-today-first-day", fresh, main(Tab.TODAY))
        shot("11-progress-empty", fresh, main(Tab.PROGRESS))
        shot("11-progress-empty", fresh, main(Tab.PROGRESS), dark = false)
        fresh.clock.advanceDays(1) // Tuesday: a rest day
        shot("12-today-rest-day", fresh, main(Tab.TODAY))
        val away = HarnessEnv().also { Seed.onboard(it); Seed.doSession(it) }
        away.clock.advanceDays(30)
        shot("13-today-return-check-in", away, main(Tab.TODAY))
    }

    @Test
    fun onboarding() {
        val env = HarnessEnv()
        shot("20-onboarding-welcome", env, Destination.Onboarding)
        shot("20-onboarding-welcome", env, Destination.Onboarding, Device.SmallHugeText)
        shot("20-onboarding-welcome", env, Destination.Onboarding, dark = false)
    }

    @Test
    fun workoutPlayer() {
        val env = HarnessEnv().also { Seed.onboard(it) }
        val sessions = env.services.sessions
        val id = runBlocking { (sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId }
        runBlocking { sessions.dispatch(id, PlayerAction.StartWorkTimer) }
        env.clock.advanceSeconds(17)
        shot("30-player-timed", env, Destination.Player(id))
        runBlocking {
            val squat = sessions.get(id)!!.plan.items.indexOfFirst { it.key == "squat" }
            sessions.dispatch(id, PlayerAction.JumpTo(squat))
        }
        shot("31-player-reps", env, Destination.Player(id))
        shot("31-player-reps", env, Destination.Player(id), Device.SmallPhone)
        shot("31-player-reps", env, Destination.Player(id), Device.LargeText)
        shot("31-player-reps", env, Destination.Player(id), Device.SmallHugeText)
        shot("31-player-reps", env, Destination.Player(id), dark = false)
        runBlocking { sessions.dispatch(id, PlayerAction.CompleteSet()) }
        env.clock.advanceSeconds(22)
        shot("32-player-rest", env, Destination.Player(id))
        shot("32-player-rest", env, Destination.Player(id), Device.SmallHugeText)
        runBlocking { sessions.dispatch(id, PlayerAction.Pause) }
        shot("33-player-paused", env, Destination.Player(id))
    }

    @Test
    fun completionAndPaywall() {
        val env = HarnessEnv().also { Seed.onboard(it) }
        val id = Seed.doSession(env, fraction = 0.6)
        shot("40-summary-partial", env, Destination.Summary(id, justFinished = true))
        shot("40-summary-partial", env, Destination.Summary(id, justFinished = true), Device.LargeText)
        shot("41-paywall", env, Destination.Paywall("test"))
        shot("41-paywall", env, Destination.Paywall("test"), dark = false)
        shot("42-equipment-editor", env, Destination.EquipmentEditor("home"))
        shot("43-exclusions", env, Destination.Exclusions)
        shot("44-schedule", env, Destination.Schedule)
        shot("45-backup", env, Destination.Backup)
        shot("46-privacy", env, Destination.Privacy)
    }
}
