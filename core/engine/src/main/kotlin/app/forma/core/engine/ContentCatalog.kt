package app.forma.core.engine

import app.forma.core.engine.catalog.DraftExercises
import app.forma.core.engine.catalog.DraftPrograms
import app.forma.core.engine.catalog.StandaloneSessions
import app.forma.core.model.Exercise
import app.forma.core.model.ExerciseId
import app.forma.core.model.ProgramTemplate
import app.forma.core.model.SessionTemplate

/** Read-only bundled content: exercises, programs and standalone sessions. */
class ContentCatalog(
    val exercises: List<Exercise>,
    val programs: List<ProgramTemplate>,
    val standaloneSessions: List<SessionTemplate>,
) {
    private val exerciseById = exercises.associateBy { it.id }
    private val programById = programs.associateBy { it.id }
    private val sessionById: Map<String, SessionTemplate> =
        (programs.flatMap { it.rotation } + standaloneSessions).associateBy { it.id }

    fun exercise(id: ExerciseId): Exercise? = exerciseById[id]
    fun requireExercise(id: ExerciseId): Exercise = exerciseById[id] ?: error("Unknown exercise $id")
    fun program(id: String): ProgramTemplate? = programById[id]
    fun sessionTemplate(id: String): SessionTemplate? = sessionById[id]
    fun programFor(sessionTemplateId: String): ProgramTemplate? =
        programs.firstOrNull { p -> p.rotation.any { it.id == sessionTemplateId } }

    fun family(name: String): List<Exercise> = exercises.filter { it.family == name }.sortedBy { it.familyRank }

    /** Returns content problems. A valid catalog returns an empty list (enforced by tests). */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        val duplicateExercises = exercises.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateExercises.isNotEmpty()) problems += "Duplicate exercise ids: $duplicateExercises"
        val duplicateSessions = (programs.flatMap { it.rotation } + standaloneSessions).groupBy { it.id }
            .filterValues { it.size > 1 }.keys
        if (duplicateSessions.isNotEmpty()) problems += "Duplicate session ids: $duplicateSessions"

        for (exercise in exercises) {
            if (exercise.cues.isEmpty()) problems += "${exercise.id} has no cues"
            if (exercise.defaultRepsMin > exercise.defaultRepsMax) problems += "${exercise.id} has an invalid rep range"
            for (sub in exercise.substitutes) {
                val target = exerciseById[sub]
                if (target == null) problems += "${exercise.id} substitute $sub does not exist"
                if (sub == exercise.id) problems += "${exercise.id} lists itself as a substitute"
            }
        }
        for (session in sessionById.values) {
            val slotIds = session.slots.map { it.id }
            if (slotIds.size != slotIds.toSet().size) problems += "${session.id} has duplicate slot ids"
            for (slot in session.slots) {
                if (slot.preferred.isEmpty()) problems += "${session.id}/${slot.id} has no preferred exercises"
                for (id in slot.preferred) {
                    val exercise = exerciseById[id]
                    when {
                        exercise == null -> problems += "${session.id}/${slot.id} prefers unknown exercise $id"
                        slot.phase !in exercise.phases ->
                            problems += "${session.id}/${slot.id}: $id is not usable in ${slot.phase}"
                        exercise.pattern !in slot.patterns ->
                            problems += "${session.id}/${slot.id}: $id pattern ${exercise.pattern} not in slot patterns"
                    }
                }
                val dose = slot.dose
                if (dose.sets < 1) problems += "${session.id}/${slot.id} has no sets"
                if (dose.maxSets < dose.sets) problems += "${session.id}/${slot.id} maxSets below sets"
            }
        }
        for (program in programs) {
            if (program.rotation.isEmpty()) problems += "${program.id} has no sessions"
            if (program.minSessionsPerWeek > program.maxSessionsPerWeek) problems += "${program.id} has invalid frequency"
        }
        return problems
    }

    companion object {
        val Default: ContentCatalog by lazy { ContentCatalog(DraftExercises, DraftPrograms, StandaloneSessions) }
    }
}
