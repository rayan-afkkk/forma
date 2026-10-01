@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.forma.core.domain

import app.forma.core.engine.ContentCatalog
import app.forma.core.engine.Constraints
import app.forma.core.engine.EasierOption
import app.forma.core.engine.EquipmentLoads
import app.forma.core.engine.GeneratedPlan
import app.forma.core.engine.GenerationInput
import app.forma.core.engine.ReplacementResult
import app.forma.core.engine.ReturnCheck
import app.forma.core.engine.ReturnStatus
import app.forma.core.engine.SessionDay
import app.forma.core.engine.TrainingHistory
import app.forma.core.engine.WeekPlanner
import app.forma.core.engine.WeekSummary
import app.forma.core.engine.WorkoutGenerator
import app.forma.core.engine.catalog.Foundations
import app.forma.core.model.Access
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExerciseId
import app.forma.core.model.NoteKind
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.PlanNote
import app.forma.core.model.PlanSource
import app.forma.core.model.ProFeature
import app.forma.core.model.ProgramEnrollment
import app.forma.core.model.ProgramTemplate
import app.forma.core.model.ReturnPlan
import app.forma.core.model.ScheduleMove
import app.forma.core.model.SessionStatus
import app.forma.core.model.SessionTemplate
import app.forma.core.model.UserState
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Which plan a screen is showing. */
sealed interface PlanKey {
    val value: String

    /** The next session of the person's program. */
    data object ProgramNext : PlanKey {
        override val value = "program"
    }

    /** A standalone or program session chosen from Explore. */
    data class Template(val id: String) : PlanKey {
        override val value get() = "template:$id"
    }

    companion object {
        fun parse(value: String): PlanKey = if (value == ProgramNext.value) ProgramNext else Template(value.removePrefix("template:"))
    }
}

/** Everything the planner reads, captured at one moment. */
data class PlanningSnapshot(
    val user: UserState,
    val equipment: List<EquipmentProfile>,
    val exclusions: Set<ExerciseId>,
    val enrollment: ProgramEnrollment?,
    val moves: List<ScheduleMove>,
    val sessions: List<WorkoutSession>,
    val access: AccessState,
    val today: LocalDate,
) {
    val finished: List<WorkoutSession> by lazy { sessions.filter { it.isFinished } }
    val inProgress: WorkoutSession? get() = sessions.firstOrNull { it.status == SessionStatus.IN_PROGRESS }
    val history: TrainingHistory by lazy { TrainingHistory.from(finished) }
    val lastSessionDate: LocalDate? by lazy { finished.maxOfOrNull { LocalDate.parse(it.localDate) } }
    val sessionDays: List<SessionDay> by lazy {
        finished.map { SessionDay(LocalDate.parse(it.localDate), it.status == SessionStatus.COMPLETED) }
    }
}

sealed interface TodayDay {
    data class Planned(val plan: GeneratedPlan, val moved: Boolean) : TodayDay
    data class Done(val sessions: List<WorkoutSession>, val nextDate: LocalDate?, val next: GeneratedPlan?) : TodayDay
    data class Rest(val nextDate: LocalDate?, val next: GeneratedPlan?) : TodayDay
}

data class TodayModel(
    val name: String,
    val today: LocalDate,
    val week: WeekSummary,
    val inProgress: WorkoutSession?,
    val returnStatus: ReturnStatus,
    val day: TodayDay,
    val access: AccessState,
    val program: ProgramTemplate,
    val upgradeOfferDue: Boolean,
    val hasEquipmentProfiles: Int,
)

data class ExploreEntry(
    val template: SessionTemplate,
    val program: ProgramTemplate?,
    val plan: GeneratedPlan,
    val locked: Boolean,
    val favorite: Boolean,
)

data class ExploreModel(
    val programs: List<ProgramEntry>,
    val sessions: List<ExploreEntry>,
)

data class ProgramEntry(
    val program: ProgramTemplate,
    val enrolled: Boolean,
    val locked: Boolean,
    /** Why the program cannot be used with the current setup, if so. */
    val unavailableReason: String?,
)

sealed interface EnrollResult {
    data object Enrolled : EnrollResult
    data object NeedsPro : EnrollResult
    data class Unavailable(val reason: String) : EnrollResult
}

class PlanningService(
    private val repos: Repositories,
    private val billing: BillingGateway,
    private val clock: AppClock,
    val catalog: ContentCatalog = ContentCatalog.Default,
    private val events: ProductEvents = ProductEvents.None,
) {
    val generator = WorkoutGenerator(catalog)

    val snapshots: Flow<PlanningSnapshot> = combine(
        repos.userState.state,
        repos.equipment.profiles,
        repos.exclusions.exclusions,
        repos.programs.enrollment,
        repos.programs.moves,
        repos.sessions.sessions,
        billing.entitlement,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val user = values[0] as UserState
        PlanningSnapshot(
            user = user,
            equipment = values[1] as List<EquipmentProfile>,
            exclusions = (values[2] as List<app.forma.core.model.ExerciseExclusion>).map { it.exerciseId }.toSet(),
            enrollment = values[3] as ProgramEnrollment?,
            moves = values[4] as List<ScheduleMove>,
            sessions = values[5] as List<WorkoutSession>,
            access = AccessState(values[6] as app.forma.core.model.EntitlementState, user.sampleSessionsUsed),
            today = clock.today(),
        )
    }

    suspend fun snapshot(): PlanningSnapshot {
        val user = repos.userState.current()
        return PlanningSnapshot(
            user = user,
            equipment = repos.equipment.all(),
            exclusions = repos.exclusions.all().map { it.exerciseId }.toSet(),
            enrollment = repos.programs.currentEnrollment(),
            moves = repos.programs.allMoves(),
            sessions = repos.sessions.all(),
            access = AccessState(billing.entitlement.value, user.sampleSessionsUsed),
            today = clock.today(),
        )
    }

    // ------------------------------------------------------------------------------------ generation

    fun activeEquipment(s: PlanningSnapshot, adjustments: PlanAdjustments = PlanAdjustments()): EquipmentProfile {
        val override = adjustments.equipmentProfileId
            ?.takeIf { s.access.can(ProFeature.MULTIPLE_EQUIPMENT_PROFILES) }
            ?.let { id -> s.equipment.firstOrNull { it.id == id } }
        return override
            ?: s.equipment.firstOrNull { it.id == s.user.profile.activeEquipmentProfileId }
            ?: s.equipment.firstOrNull()
            ?: EquipmentProfile.bodyweight("bodyweight", s.user.settings.displayUnit)
    }

    fun constraints(s: PlanningSnapshot, adjustments: PlanAdjustments = PlanAdjustments()): Constraints =
        generator.constraintsFor(s.user.profile, activeEquipment(s, adjustments), s.exclusions, adjustments.quiet)

    /** The program in effect. A Pro program falls back to Foundations while Pro is inactive. */
    fun effectiveProgram(s: PlanningSnapshot): Pair<ProgramTemplate, PlanNote?> {
        val enrolled = s.enrollment?.programId?.let { catalog.program(it) } ?: Foundations
        if (s.access.canUse(enrolled.access)) return enrolled to null
        return Foundations to PlanNote(
            NoteKind.PRO_LIMIT,
            "${enrolled.name} is part of Pro, so today's plan comes from Foundations. Your history is unchanged.",
        )
    }

    fun templateFor(s: PlanningSnapshot, key: PlanKey): Pair<SessionTemplate, PlanSource>? = when (key) {
        PlanKey.ProgramNext -> {
            val (program, _) = effectiveProgram(s)
            val index = s.enrollment?.rotationIndex ?: 0
            val template = program.rotation[index % program.rotation.size]
            template to PlanSource.Program(program.id, template.id, index)
        }
        is PlanKey.Template -> catalog.sessionTemplate(key.id)?.let { it to PlanSource.Template(it.id) }
    }

    fun generate(s: PlanningSnapshot, key: PlanKey, adjustments: PlanAdjustments): GeneratedPlan? {
        val (template, source) = templateFor(s, key) ?: return null
        val returnPlan = s.user.returnPlan
        // The person's usual available time shapes program sessions unless they chose a time today.
        val effective = if (key == PlanKey.ProgramNext && adjustments.targetMinutes == null) {
            adjustments.copy(targetMinutes = s.user.profile.sessionMinutes)
        } else {
            adjustments
        }
        val generated = generator.generate(
            GenerationInput(
                template = template,
                source = source,
                planId = "${s.today}:${key.value}",
                profile = s.user.profile,
                equipment = activeEquipment(s, adjustments),
                exclusions = s.exclusions,
                history = s.history,
                adaptive = s.access.adaptive,
                returnEase = returnPlan != null && returnPlan.easeBack && returnPlan.sessionsRemaining > 0,
                adjustments = effective,
            ),
        )
        // Keep the person's own choices as the recorded adjustments (not the implicit usual time).
        val plan = generated.plan.copy(adjustments = adjustments)
        val fallback = if (key == PlanKey.ProgramNext) effectiveProgram(s).second else null
        return generated.copy(plan = if (fallback != null) plan.copy(notes = plan.notes + fallback) else plan)
    }

    fun observePlan(key: PlanKey): Flow<GeneratedPlan?> =
        repos.programs.adjustments(clock.today().toString(), key.value).flatMapLatest { adjustments ->
            snapshots.map { generate(it, key, adjustments) }
        }.distinctUntilChanged()

    suspend fun currentPlan(key: PlanKey): GeneratedPlan? =
        generate(snapshot(), key, repos.programs.currentAdjustments(clock.today().toString(), key.value))

    suspend fun adjustments(key: PlanKey): PlanAdjustments =
        repos.programs.currentAdjustments(clock.today().toString(), key.value)

    fun observeAdjustments(key: PlanKey): Flow<PlanAdjustments> =
        repos.programs.adjustments(clock.today().toString(), key.value)

    /** Shows what a change would do without saving it. */
    suspend fun previewAdjustments(key: PlanKey, adjustments: PlanAdjustments): GeneratedPlan? =
        generate(snapshot(), key, adjustments)

    suspend fun setAdjustments(key: PlanKey, adjustments: PlanAdjustments) {
        val s = snapshot()
        // Switching equipment for one day is a Pro feature; never store an override a free user cannot use.
        val allowed = if (adjustments.equipmentProfileId != null && !s.access.can(ProFeature.MULTIPLE_EQUIPMENT_PROFILES)) {
            adjustments.copy(equipmentProfileId = null)
        } else {
            adjustments
        }
        repos.programs.setAdjustments(clock.today().toString(), key.value, allowed)
    }

    suspend fun updateAdjustments(key: PlanKey, transform: (PlanAdjustments) -> PlanAdjustments) =
        setAdjustments(key, transform(adjustments(key)))

    // ------------------------------------------------------------------------------------ replacements

    suspend fun replacementOptions(key: PlanKey, itemKey: String): ReplacementResult? {
        val s = snapshot()
        val adjustments = adjustments(key)
        val plan = generate(s, key, adjustments)?.plan ?: return null
        val item = plan.item(itemKey) ?: return null
        val others = plan.items.filter { it.key != itemKey }.map { it.exerciseId }.toSet()
        return generator.substitutions.replacements(item, others, constraints(s, adjustments))
    }

    suspend fun easierOption(key: PlanKey, itemKey: String): EasierOption? {
        val s = snapshot()
        val adjustments = adjustments(key)
        val plan = generate(s, key, adjustments)?.plan ?: return null
        val item = plan.item(itemKey) ?: return null
        val others = plan.items.filter { it.key != itemKey }.map { it.exerciseId }.toSet()
        return generator.substitutions.easierOption(item, others, constraints(s, adjustments), activeEquipment(s, adjustments), s.history)
    }

    /** Replace for this workout only. Never touches permanent exclusions. */
    suspend fun replaceForToday(key: PlanKey, itemKey: String, exerciseId: ExerciseId) {
        updateAdjustments(key) { it.copy(replacements = it.replacements + (itemKey to exerciseId), easierItems = it.easierItems - itemKey) }
        events.track(ProductEvent.EXERCISE_REPLACED)
    }

    suspend fun makeEasierForToday(key: PlanKey, itemKey: String) =
        updateAdjustments(key) { it.copy(easierItems = it.easierItems + itemKey) }

    suspend fun omitForToday(key: PlanKey, itemKey: String) =
        updateAdjustments(key) { it.copy(omitted = it.omitted + itemKey) }

    suspend fun resetToday(key: PlanKey) = setAdjustments(key, PlanAdjustments())

    // ------------------------------------------------------------------------------------ exclusions

    /** Permanent exclusion. Survives restarts, program changes and regeneration until restored. */
    suspend fun exclude(exerciseId: ExerciseId) {
        repos.exclusions.add(exerciseId, clock.nowMillis())
        events.track(ProductEvent.PERMANENT_EXCLUSION_ADDED)
    }

    suspend fun restore(exerciseId: ExerciseId) = repos.exclusions.remove(exerciseId)

    // ------------------------------------------------------------------------------------ schedule

    fun returnStatus(s: PlanningSnapshot): ReturnStatus = ReturnCheck.evaluate(s.lastSessionDate, s.today, s.user.returnPlan)

    suspend fun answerReturnCheckIn(easeBack: Boolean) {
        val s = snapshot()
        val status = returnStatus(s)
        repos.userState.update {
            it.copy(
                returnPlan = ReturnPlan(
                    answeredOn = s.today.toString(),
                    easeBack = easeBack,
                    sessionsRemaining = if (easeBack) ReturnCheck.easeSessionsFor(status).coerceAtLeast(1) else 0,
                ),
            )
        }
    }

    suspend fun moveSession(from: LocalDate, to: LocalDate) {
        repos.programs.addMove(ScheduleMove(from.toString(), to.toString()))
    }

    fun moveTargets(s: PlanningSnapshot): List<LocalDate> =
        WeekPlanner.moveTargets(s.today, s.today, s.user.schedule, s.moves)

    suspend fun enroll(programId: String): EnrollResult {
        val s = snapshot()
        val program = catalog.program(programId) ?: return EnrollResult.Unavailable("This program is not available.")
        if (!s.access.canUse(program.access)) return EnrollResult.NeedsPro
        unavailableReason(program, s)?.let { return EnrollResult.Unavailable(it) }
        if (s.enrollment?.programId == programId) return EnrollResult.Enrolled
        repos.programs.setEnrollment(ProgramEnrollment(programId, clock.nowMillis(), 0))
        return EnrollResult.Enrolled
    }

    private fun unavailableReason(program: ProgramTemplate, s: PlanningSnapshot): String? =
        if (program.requiresDumbbells && !activeEquipment(s).hasDumbbells) {
            "${program.name} needs dumbbells. Add them to your equipment to use this program."
        } else {
            null
        }

    // ------------------------------------------------------------------------------------ screens

    fun observeToday(): Flow<TodayModel> =
        repos.programs.adjustments(clock.today().toString(), PlanKey.ProgramNext.value).flatMapLatest { adjustments ->
            snapshots.map { s -> today(s, adjustments) }
        }

    fun today(s: PlanningSnapshot, adjustments: PlanAdjustments): TodayModel {
        val week = WeekPlanner.summary(s.today, s.user.schedule, s.moves, s.sessionDays)
        val doneToday = s.finished.filter { it.localDate == s.today.toString() }
        val plannedToday = WeekPlanner.isPlanned(s.today, s.user.schedule, s.moves)
        val nextDate = WeekPlanner.nextPlannedDate(s.today, s.user.schedule, s.moves)
        val plan = generate(s, PlanKey.ProgramNext, adjustments)
        val day = when {
            doneToday.isNotEmpty() -> TodayDay.Done(doneToday, nextDate, plan)
            plannedToday && plan != null -> TodayDay.Planned(plan, moved = s.moves.any { it.toDate == s.today.toString() })
            else -> TodayDay.Rest(nextDate, plan)
        }
        return TodayModel(
            name = s.user.profile.name,
            today = s.today,
            week = week,
            inProgress = s.inProgress,
            returnStatus = if (s.inProgress == null) returnStatus(s) else ReturnStatus.None,
            day = day,
            access = s.access,
            program = effectiveProgram(s).first,
            upgradeOfferDue = !s.access.isPro && s.access.sampleRemaining == 0 && !s.user.upgradeOfferSeen,
            hasEquipmentProfiles = s.equipment.size,
        )
    }

    fun observeExplore(): Flow<ExploreModel> = combine(snapshots, repos.favorites.favorites) { s, favorites ->
        val (effective, _) = effectiveProgram(s)
        val programs = catalog.programs.map { program ->
            ProgramEntry(
                program = program,
                enrolled = (s.enrollment?.programId ?: Foundations.id) == program.id && effective.id == program.id,
                locked = !s.access.canUse(program.access),
                unavailableReason = unavailableReason(program, s),
            )
        }
        val sessions = (catalog.standaloneSessions + catalog.programs.flatMap { p -> p.rotation }).mapNotNull { template ->
            val program = catalog.programFor(template.id)
            val access = program?.access ?: template.access
            val plan = generate(s, PlanKey.Template(template.id), PlanAdjustments()) ?: return@mapNotNull null
            ExploreEntry(template, program, plan, locked = !s.access.canUse(access), favorite = template.id in favorites)
        }
        ExploreModel(programs, sessions)
    }

    suspend fun setFavorite(templateId: String, favorite: Boolean) = repos.favorites.set(templateId, favorite)

    fun accessFor(template: SessionTemplate): Access = catalog.programFor(template.id)?.access ?: template.access

    companion object {
        fun describeEquipment(profile: EquipmentProfile): String = EquipmentLoads.describe(profile)
        val DefaultUnit = WeightUnit.KG
    }
}
