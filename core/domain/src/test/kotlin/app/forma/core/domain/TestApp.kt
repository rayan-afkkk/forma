package app.forma.core.domain

import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.Tier
import app.forma.core.model.WeightUnit

/** Wires real services over in-memory repositories, a fake clock and development billing. */
class TestApp(
    val repos: Repositories = Repositories.inMemory(),
    val clock: FakeClock = FakeClock(),
    val billing: DevelopmentBilling = DevelopmentBilling(Tier.FREE, clock),
) {
    private var counter = 0
    val ids = IdGenerator { "session-${++counter}" }
    val planning = PlanningService(repos, billing, clock)
    val sessions = SessionService(repos, planning, clock, ids)
    val onboarding = OnboardingService(repos, planning, clock)
    val backup = BackupService(repos, clock)
    val reset = DataResetService(repos)

    /** Simulates process death: new services over the same stored data. */
    fun recreate(): TestApp = TestApp(repos, clock, billing)

    suspend fun onboard(
        equipment: EquipmentProfile = dumbbells,
        days: Set<Int> = setOf(1, 3, 5),
        minutes: Int = 45,
        level: ExperienceLevel = ExperienceLevel.SOME,
    ) {
        onboarding.complete(
            OnboardingAnswers(
                name = "Sam", goal = Goal.GENERAL_FITNESS, experience = level, equipment = equipment,
                sessionMinutes = minutes, days = days, quiet = false, limitedSpace = false,
            ),
        )
    }

    /** Starts today's program session and logs every set at its default values. */
    suspend fun doWholeSession(key: PlanKey = PlanKey.ProgramNext): String {
        val id = (sessions.start(key) as StartResult.Started).sessionId
        logAllSets(id)
        return id
    }

    suspend fun logAllSets(id: String) {
        var guard = 0
        while (guard++ < 200) {
            val (_, state) = sessions.loadPlayer(id) ?: return
            if (state.phase == app.forma.core.model.PlayerPhase.FINISHED) return
            if (state.phase == app.forma.core.model.PlayerPhase.REST) sessions.dispatch(id, PlayerAction.SkipRest)
            sessions.dispatch(id, PlayerAction.CompleteSet())
        }
    }

    companion object {
        val dumbbells = EquipmentProfile(
            "home", "Home", WeightUnit.KG,
            listOf(EquipmentItem.AdjustableDumbbells(250, 2000, 250, 2)),
        )
        val bodyweight = EquipmentProfile("home", "Home", WeightUnit.KG, emptyList())
    }
}
