package app.forma.harness

import app.forma.core.domain.OnboardingAnswers
import app.forma.core.domain.PlanKey
import app.forma.core.domain.PlayerAction
import app.forma.core.domain.StartResult
import app.forma.core.model.Difficulty
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.PlayerPhase
import app.forma.core.model.WeightUnit
import kotlinx.coroutines.runBlocking

/** Seeds realistic local data by driving the real services, never by writing fake records. */
object Seed {
    val homeDumbbells = EquipmentProfile(
        id = "home", name = "Home", unit = WeightUnit.KG,
        items = listOf(EquipmentItem.AdjustableDumbbells(250, 2000, 250, 2), EquipmentItem.Bench),
    )

    fun onboard(env: HarnessEnv, name: String = "Sam", minutes: Int = 30, equipment: EquipmentProfile = homeDumbbells) = runBlocking {
        env.services.onboarding.complete(
            OnboardingAnswers(
                name = name, goal = Goal.GENERAL_FITNESS, experience = ExperienceLevel.SOME, equipment = equipment,
                sessionMinutes = minutes, days = setOf(1, 3, 5), quiet = false, limitedSpace = false,
            ),
        )
    }

    /** Performs the next program session through the player, logging every set at its defaults. */
    fun doSession(env: HarnessEnv, difficulty: Difficulty = Difficulty.JUST_RIGHT, fraction: Double = 1.0): String = runBlocking {
        val sessions = env.services.sessions
        val id = (sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        val total = sessions.get(id)!!.plannedSetCount
        var logged = 0
        var guard = 0
        while (guard++ < 300 && logged < total * fraction) {
            val (_, state) = sessions.loadPlayer(id) ?: break
            if (state.phase == PlayerPhase.FINISHED) break
            if (state.phase == PlayerPhase.REST) sessions.dispatch(id, PlayerAction.SkipRest)
            env.clock.advanceSeconds(45)
            sessions.dispatch(id, PlayerAction.CompleteSet())
            logged++
        }
        sessions.finish(id)
        sessions.saveFeedback(id, difficulty, "")
        id
    }

    /** Two weeks of history: Mon/Wed/Fri sessions with one partial session and one missed day. */
    fun history(env: HarnessEnv) {
        onboard(env)
        val plan = listOf(0L to 1.0, 2L to 1.0, 4L to 0.5, 7L to 1.0, 11L to 1.0)
        var day = 0L
        for ((offset, fraction) in plan) {
            env.clock.advanceDays(offset - day)
            day = offset
            doSession(env, fraction = fraction)
        }
        env.clock.advanceDays(14 - day) // Monday of week three, a planned day
    }
}
