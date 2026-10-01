package app.forma.android.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Room schema, version 1. Every schema change must add a Migration in Migrations.kt and keep the
 * exported schema JSON in app/schemas under version control; destructive migration is never used,
 * so people never lose their history on update. See docs/DATA.md.
 *
 * Times are UTC epoch milliseconds. Sessions also store their local date and time-zone ID so a
 * session stays on the calendar day it happened even after travel or a time-zone change.
 * Weights are stored as integer hundredths plus an explicit unit, exactly as logged.
 */

@Entity(tableName = "equipment_profiles")
data class EquipmentProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    /** JSON list of equipment items (fixed dumbbells, adjustable dumbbells, bench, ...). */
    val itemsJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "exercise_exclusions")
data class ExclusionEntity(
    @PrimaryKey val exerciseId: String,
    val createdAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val templateId: String,
    val createdAt: Long,
)

/** Single row (id = 1): the program the person follows and where they are in its rotation. */
@Entity(tableName = "program_enrollment")
data class EnrollmentEntity(
    @PrimaryKey val id: Int = 1,
    val programId: String,
    val startedAt: Long,
    val rotationIndex: Int,
)

@Entity(tableName = "schedule_moves")
data class ScheduleMoveEntity(
    @PrimaryKey val fromDate: String,
    val toDate: String,
)

/** One-day adjustments ("Adjust today", temporary replacements), keyed by local date and plan. */
@Entity(tableName = "plan_adjustments", primaryKeys = ["date", "planKey"])
data class PlanAdjustmentEntity(
    val date: String,
    val planKey: String,
    val json: String,
)

@Entity(tableName = "sessions", indices = [Index("startedAt"), Index("status")])
data class SessionEntity(
    @PrimaryKey val id: String,
    /** JSON snapshot of the plan as performed. Never rewritten by later catalog changes. */
    val planJson: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long?,
    val activeSeconds: Int,
    val zoneId: String,
    val localDate: String,
    val skippedKeysJson: String,
    val feedbackDifficulty: String?,
    val feedbackNote: String?,
    val feedbackUpdatedAt: Long?,
    val countedAsSample: Boolean,
    val ruleVersion: String,
)

@Entity(
    tableName = "set_logs",
    primaryKeys = ["sessionId", "itemKey", "setNumber"],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLogEntity(
    val sessionId: String,
    val itemKey: String,
    val setNumber: Int,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int?,
    val seconds: Int?,
    val loadHundredths: Int?,
    val loadUnit: String?,
    val completedAt: Long,
)

/** Single row (id = 1): the player position and timers for the session in progress. */
@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = 1,
    val sessionId: String,
    val json: String,
    val updatedAt: Long,
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>,
)

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment_profiles ORDER BY name")
    fun observeAll(): Flow<List<EquipmentProfileEntity>>

    @Query("SELECT * FROM equipment_profiles ORDER BY name")
    suspend fun all(): List<EquipmentProfileEntity>

    @Upsert
    suspend fun upsert(entity: EquipmentProfileEntity)

    @Query("DELETE FROM equipment_profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM equipment_profiles")
    suspend fun clear()
}

@Dao
interface ExclusionDao {
    @Query("SELECT * FROM exercise_exclusions ORDER BY createdAt")
    fun observeAll(): Flow<List<ExclusionEntity>>

    @Query("SELECT * FROM exercise_exclusions ORDER BY createdAt")
    suspend fun all(): List<ExclusionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ExclusionEntity)

    @Query("DELETE FROM exercise_exclusions WHERE exerciseId = :exerciseId")
    suspend fun delete(exerciseId: String)

    @Query("DELETE FROM exercise_exclusions")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT templateId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT templateId FROM favorites")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE templateId = :templateId")
    suspend fun delete(templateId: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program_enrollment WHERE id = 1")
    fun observeEnrollment(): Flow<EnrollmentEntity?>

    @Query("SELECT * FROM program_enrollment WHERE id = 1")
    suspend fun enrollment(): EnrollmentEntity?

    @Upsert
    suspend fun upsertEnrollment(entity: EnrollmentEntity)

    @Query("DELETE FROM program_enrollment")
    suspend fun clearEnrollment()

    @Query("SELECT * FROM schedule_moves ORDER BY fromDate")
    fun observeMoves(): Flow<List<ScheduleMoveEntity>>

    @Query("SELECT * FROM schedule_moves ORDER BY fromDate")
    suspend fun moves(): List<ScheduleMoveEntity>

    @Upsert
    suspend fun upsertMove(entity: ScheduleMoveEntity)

    @Query("DELETE FROM schedule_moves")
    suspend fun clearMoves()

    @Query("SELECT json FROM plan_adjustments WHERE date = :date AND planKey = :planKey")
    fun observeAdjustments(date: String, planKey: String): Flow<String?>

    @Query("SELECT json FROM plan_adjustments WHERE date = :date AND planKey = :planKey")
    suspend fun adjustments(date: String, planKey: String): String?

    @Upsert
    suspend fun upsertAdjustments(entity: PlanAdjustmentEntity)

    /** One-day adjustments are only useful on their day; older rows are pruned. */
    @Query("DELETE FROM plan_adjustments WHERE date < :beforeDate")
    suspend fun pruneAdjustments(beforeDate: String)

    @Query("DELETE FROM plan_adjustments")
    suspend fun clearAdjustments()
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    suspend fun all(): List<SessionWithSets>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionWithSets?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SessionEntity>)

    @Query("UPDATE sessions SET planJson = :planJson WHERE id = :id")
    suspend fun updatePlan(id: String, planJson: String)

    @Query("UPDATE sessions SET skippedKeysJson = :json WHERE id = :id")
    suspend fun updateSkipped(id: String, json: String)

    /**
     * Completion happens at most once: the WHERE clause only matches an in-progress session, so a
     * repeated tap or retry updates zero rows.
     */
    @Query(
        "UPDATE sessions SET status = :status, endedAt = :endedAt, activeSeconds = :activeSeconds, " +
            "countedAsSample = :countedAsSample WHERE id = :id AND status = 'IN_PROGRESS'",
    )
    suspend fun finish(id: String, status: String, endedAt: Long, activeSeconds: Int, countedAsSample: Boolean): Int

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE sessions SET feedbackDifficulty = :difficulty, feedbackNote = :note, feedbackUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFeedback(id: String, difficulty: String?, note: String, updatedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sessions")
    suspend fun clear()

    /** Keyed by (session, item, set number): logging the same set again replaces it, never duplicates. */
    @Upsert
    suspend fun upsertSet(entity: SetLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(entities: List<SetLogEntity>)

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId AND itemKey = :itemKey AND setNumber = :setNumber")
    suspend fun deleteSet(sessionId: String, itemKey: String, setNumber: Int)

    @Query("SELECT * FROM active_session WHERE id = 1")
    fun observeActive(): Flow<ActiveSessionEntity?>

    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun active(): ActiveSessionEntity?

    @Upsert
    suspend fun upsertActive(entity: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun clearActive()
}

@Database(
    entities = [
        EquipmentProfileEntity::class,
        ExclusionEntity::class,
        FavoriteEntity::class,
        EnrollmentEntity::class,
        ScheduleMoveEntity::class,
        PlanAdjustmentEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        ActiveSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FormaDatabase : RoomDatabase() {
    abstract fun equipment(): EquipmentDao
    abstract fun exclusions(): ExclusionDao
    abstract fun favorites(): FavoriteDao
    abstract fun programs(): ProgramDao
    abstract fun sessions(): SessionDao

    companion object {
        const val NAME = "forma.db"
    }
}
