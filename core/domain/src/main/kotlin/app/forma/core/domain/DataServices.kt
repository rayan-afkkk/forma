package app.forma.core.domain

import app.forma.core.engine.catalog.Foundations
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExerciseExclusion
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.PlanSource
import app.forma.core.model.ProgramEnrollment
import app.forma.core.model.Schedule
import app.forma.core.model.ScheduleMove
import app.forma.core.model.SessionStatus
import app.forma.core.model.UserProfile
import app.forma.core.model.UserState
import app.forma.core.model.WorkoutSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// ------------------------------------------------------------------------------------------ onboarding

/** Answers collected during onboarding. Everything can be changed later in You. */
data class OnboardingAnswers(
    val name: String,
    val goal: Goal,
    val experience: ExperienceLevel,
    val equipment: EquipmentProfile,
    val sessionMinutes: Int,
    val days: Set<Int>,
    val quiet: Boolean,
    val limitedSpace: Boolean,
)

class OnboardingService(
    private val repos: Repositories,
    private val planning: PlanningService,
    private val clock: AppClock,
    private val events: ProductEvents = ProductEvents.None,
) {
    fun profileFrom(answers: OnboardingAnswers) = UserProfile(
        name = answers.name.trim().take(40),
        goal = answers.goal,
        experience = answers.experience,
        sessionMinutes = answers.sessionMinutes,
        quiet = answers.quiet,
        limitedSpace = answers.limitedSpace,
        activeEquipmentProfileId = answers.equipment.id,
    )

    /** A real preview of the first workout, generated with the same rules as Today, without saving anything. */
    suspend fun previewFirstWorkout(answers: OnboardingAnswers): app.forma.core.engine.GeneratedPlan? {
        val base = planning.snapshot()
        val user = base.user.copy(profile = profileFrom(answers), schedule = Schedule(answers.days))
        val s = base.copy(user = user, equipment = listOf(answers.equipment), enrollment = null)
        return planning.generate(s, PlanKey.ProgramNext, app.forma.core.model.PlanAdjustments())
    }

    suspend fun complete(answers: OnboardingAnswers) {
        repos.transactions.run {
            repos.equipment.upsert(answers.equipment)
            repos.userState.update {
                it.copy(
                    onboardingComplete = true,
                    safetyNoteAcknowledged = true,
                    profile = profileFrom(answers),
                    schedule = Schedule(answers.days),
                    settings = it.settings.copy(displayUnit = answers.equipment.unit),
                )
            }
            if (repos.programs.currentEnrollment() == null) {
                repos.programs.setEnrollment(ProgramEnrollment(Foundations.id, clock.nowMillis(), 0))
            }
        }
        events.track(ProductEvent.ONBOARDING_COMPLETED)
    }
}

// ------------------------------------------------------------------------------------------ backup

/**
 * The export format. Versioned so future versions can migrate older files. Contains only what the
 * person created on this device; purchases are restored from Google Play instead.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val user: UserState,
    val equipment: List<EquipmentProfile>,
    val exclusions: List<ExerciseExclusion>,
    val favorites: List<String>,
    val enrollment: ProgramEnrollment?,
    val scheduleMoves: List<ScheduleMove>,
    val sessions: List<WorkoutSession>,
) {
    companion object {
        const val FORMAT = "forma-backup"
        const val VERSION = 1
    }
}

sealed interface ImportResult {
    data class Imported(val sessions: Int) : ImportResult
    data class Invalid(val reason: String) : ImportResult
}

object BackupCodec {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): Result<BackupFile> = try {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        when {
            file.format != BackupFile.FORMAT -> Result.failure(IllegalArgumentException("This isn't a Forma backup file."))
            file.version > BackupFile.VERSION -> Result.failure(
                IllegalArgumentException("This backup was made by a newer version of the app. Update the app and try again."),
            )
            else -> Result.success(file)
        }
    } catch (e: SerializationException) {
        Result.failure(IllegalArgumentException("The file couldn't be read. It may be damaged or not a Forma backup."))
    } catch (e: IllegalArgumentException) {
        Result.failure(IllegalArgumentException("The file couldn't be read. It may be damaged or not a Forma backup."))
    }
}

class BackupService(
    private val repos: Repositories,
    private val clock: AppClock,
) {
    /** Exports finished sessions and settings. An in-progress session is not included. */
    suspend fun export(): String = BackupCodec.encode(
        BackupFile(
            exportedAt = clock.nowMillis(),
            user = repos.userState.current(),
            equipment = repos.equipment.all(),
            exclusions = repos.exclusions.all(),
            favorites = repos.favorites.all().sorted(),
            enrollment = repos.programs.currentEnrollment(),
            scheduleMoves = repos.programs.allMoves(),
            sessions = repos.sessions.all().filter { it.status != SessionStatus.IN_PROGRESS },
        ),
    )

    /** A short CSV of every logged set, for spreadsheets. Loads keep their original unit. */
    suspend fun exportSetsCsv(): String {
        val rows = mutableListOf("date,session,exercise,set,reps,seconds,load,unit,status")
        for (session in repos.sessions.all().filter { it.isFinished }.sortedBy { it.startedAt }) {
            for (set in session.sets.sortedWith(compareBy({ it.completedAt }, { it.setNumber }))) {
                rows += listOf(
                    session.localDate,
                    csv(session.plan.title),
                    csv(set.exerciseName),
                    set.setNumber.toString(),
                    set.reps?.toString().orEmpty(),
                    set.seconds?.toString().orEmpty(),
                    set.load?.let { app.forma.core.model.formatAmount(it.amount) }.orEmpty(),
                    set.load?.unit?.symbol.orEmpty(),
                    session.status.name.lowercase(),
                ).joinToString(",")
            }
        }
        return rows.joinToString("\n") + "\n"
    }

    private fun csv(value: String) = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

    /**
     * Replaces local data with the backup's contents. The free-sample counter keeps the higher of
     * the two values so importing cannot reset it.
     */
    suspend fun import(text: String): ImportResult {
        val file = BackupCodec.decode(text).getOrElse { return ImportResult.Invalid(it.message ?: "The file couldn't be read.") }
        val sessions = file.sessions.filter { it.status != SessionStatus.IN_PROGRESS }
        repos.transactions.run {
            val current = repos.userState.current()
            repos.equipment.clear()
            file.equipment.forEach { repos.equipment.upsert(it) }
            repos.exclusions.clear()
            file.exclusions.forEach { repos.exclusions.add(it.exerciseId, it.createdAt) }
            repos.favorites.clear()
            file.favorites.forEach { repos.favorites.set(it, true) }
            repos.programs.clear()
            repos.programs.setEnrollment(file.enrollment)
            file.scheduleMoves.forEach { repos.programs.addMove(it) }
            repos.sessions.clear()
            repos.sessions.replaceAll(sessions)
            repos.userState.update {
                file.user.copy(sampleSessionsUsed = maxOf(file.user.sampleSessionsUsed, current.sampleSessionsUsed))
            }
        }
        return ImportResult.Imported(sessions.size)
    }
}

// ------------------------------------------------------------------------------------------ deletion

class DataResetService(private val repos: Repositories) {
    /**
     * Removes everything stored on this device: profile, equipment, exclusions, favorites, program,
     * schedule, workout history and settings. Purchases are held by Google Play and are unaffected.
     */
    suspend fun deleteAll() {
        repos.transactions.run {
            repos.sessions.clear()
            repos.programs.clear()
            repos.favorites.clear()
            repos.exclusions.clear()
            repos.equipment.clear()
            repos.userState.clear()
        }
    }
}

internal fun PlanSource.isProgram() = this is PlanSource.Program
