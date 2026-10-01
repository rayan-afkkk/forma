package app.forma.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.forma.core.domain.DevelopmentBilling
import app.forma.core.domain.FakeClock
import app.forma.core.domain.FinishResult
import app.forma.core.domain.OnboardingAnswers
import app.forma.core.domain.PlanKey
import app.forma.core.domain.PlayerAction
import app.forma.core.domain.Repositories
import app.forma.core.domain.StartResult
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.SessionStatus
import app.forma.core.model.Tier
import app.forma.core.model.WeightUnit
import app.forma.presentation.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Runs the real domain services over the Room and DataStore repositories (Robolectric).
 * Not run in the authoring environment (Google Maven was unreachable); see docs/TEST_RESULTS.md.
 */
@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var db: FormaDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var services: AppServices
    private val clock = FakeClock()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FormaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("user.preferences_pb") }
        val now = { clock.nowMillis() }
        val repos = Repositories(
            userState = DataStoreUserStateRepository(store),
            equipment = RoomEquipmentRepository(db.equipment(), now),
            exclusions = RoomExclusionRepository(db.exclusions()),
            favorites = RoomFavoritesRepository(db.favorites(), now),
            programs = RoomProgramRepository(db.programs()),
            sessions = RoomSessionRepository(db.sessions()),
            transactions = RoomTransactionRunner(db),
        )
        services = AppServices(repos, clock, DevelopmentBilling(Tier.FREE, clock))
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `whole session persists, and finishing twice records it once`() = runBlocking {
        services.onboarding.complete(
            OnboardingAnswers(
                name = "Sam", goal = Goal.GENERAL_FITNESS, experience = ExperienceLevel.SOME,
                equipment = EquipmentProfile("home", "Home", WeightUnit.KG, listOf(EquipmentItem.AdjustableDumbbells(250, 2000, 250, 2))),
                sessionMinutes = 30, days = setOf(1, 3, 5), quiet = false, limitedSpace = false,
            ),
        )
        val id = (services.sessions.start(PlanKey.ProgramNext) as StartResult.Started).sessionId
        services.sessions.dispatch(id, PlayerAction.CompleteSet())
        // A retried write of the same set must not duplicate it.
        val logged = services.repos.sessions.get(id)!!.sets.single()
        services.repos.sessions.upsertSet(logged)
        assertEquals(1, services.repos.sessions.get(id)!!.sets.size)

        assertIs<FinishResult.Finished>(services.sessions.finish(id))
        assertIs<FinishResult.AlreadyFinished>(services.sessions.finish(id))
        val stored = services.repos.sessions.get(id)!!
        assertTrue(stored.status == SessionStatus.PARTIAL || stored.status == SessionStatus.COMPLETED)
        assertEquals(1, services.repos.sessions.all().size)
    }

    @Test
    fun `exclusions and preferences survive a new repository instance`() = runBlocking {
        services.planning.exclude("push_up")
        services.repos.userState.update { it.copy(profile = it.profile.copy(name = "Alex")) }
        assertEquals(listOf("push_up"), RoomExclusionRepository(db.exclusions()).all().map { it.exerciseId })
        assertEquals("Alex", services.repos.userState.current().profile.name)
    }
}
