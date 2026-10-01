package app.forma.presentation

import app.forma.core.engine.ContentCatalog
import app.forma.core.engine.GeneratedPlan
import app.forma.core.model.ChangeKind
import app.forma.core.model.Execution
import app.forma.core.model.MovementPattern
import app.forma.core.model.NoteKind
import app.forma.core.model.Phase
import app.forma.core.model.PlanSource
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutPlan

/** How a change to an exercise should read. Never conveyed by colour alone. */
enum class ChangeTone { UP, DOWN, SAME, SWAP, INFO }

data class ItemUi(
    val key: String,
    val exerciseId: String,
    val name: String,
    val detail: String,
    val rest: String,
    val pattern: MovementPattern,
    val timed: Boolean,
    val cues: List<String>,
    val summary: String,
    val optional: Boolean,
    val change: String? = null,
    val changeTone: ChangeTone? = null,
    val replacedFrom: String? = null,
)

data class SectionUi(val title: String, val items: List<ItemUi>)

data class NoteUi(val kind: NoteKind, val text: String)

data class PlanUi(
    val key: String,
    val title: String,
    val eyebrow: String,
    val purpose: String,
    val minutes: Int,
    val durationText: String,
    val equipmentText: String,
    val countText: String,
    val sections: List<SectionUi>,
    /** The plain-language reason for today's plan, if any. */
    val why: String?,
    val notes: List<NoteUi>,
    val impractical: String?,
    val locked: Boolean,
    val adjusted: Boolean,
) {
    val items: List<ItemUi> get() = sections.flatMap { it.items }
}

object PlanMapper {

    fun toUi(
        generated: GeneratedPlan,
        catalog: ContentCatalog,
        key: String,
        locked: Boolean = false,
    ): PlanUi = toUi(generated.plan, catalog, key, locked, generated.impractical?.let {
        "A ${it.requestedMinutes}-minute version wouldn't leave room for meaningful main work. This is the shortest practical version, about ${it.shortestMinutes} min."
    })

    fun toUi(plan: WorkoutPlan, catalog: ContentCatalog, key: String, locked: Boolean = false, impractical: String? = null): PlanUi {
        val program = (plan.source as? PlanSource.Program)?.let { catalog.program(it.programId) }
        val sections = Phase.entries.mapNotNull { phase ->
            val items = plan.items.filter { it.phase == phase }
            if (items.isEmpty()) null else SectionUi(phase.label, items.map { item(it, catalog) })
        }
        val mains = plan.items.count { it.phase == Phase.MAIN }
        return PlanUi(
            key = key,
            title = plan.title,
            eyebrow = listOfNotNull(program?.name, plan.focus).distinct().joinToString(" · "),
            purpose = plan.purpose,
            minutes = plan.estimatedMinutes,
            durationText = Format.aboutMinutes(plan.estimatedMinutes),
            equipmentText = plan.equipmentSummary,
            countText = "${Format.count(mains, "exercise")} · ${Format.count(plan.totalSets, "set")}",
            sections = sections,
            why = plan.notes.firstOrNull { it.kind == NoteKind.SUMMARY }?.text,
            notes = plan.notes.filter { it.kind != NoteKind.SUMMARY && (impractical == null || it.kind != NoteKind.SHORTENED) }
                .map { NoteUi(it.kind, it.text) },
            impractical = impractical,
            locked = locked,
            adjusted = !plan.adjustments.isEmpty,
        )
    }

    fun item(item: WorkoutItem, catalog: ContentCatalog): ItemUi {
        val exercise = catalog.exercise(item.exerciseId)
        val change = item.change?.takeIf { it.kind != ChangeKind.FIRST_TIME || item.load != null }
        return ItemUi(
            key = item.key,
            exerciseId = item.exerciseId,
            name = item.exerciseName,
            detail = Format.itemDetail(item),
            rest = Format.rest(item.restSeconds),
            pattern = item.pattern,
            timed = item.execution == Execution.TIME,
            cues = item.cues,
            summary = exercise?.summary.orEmpty(),
            optional = item.optional,
            change = change?.message,
            changeTone = change?.kind?.let(::tone),
            replacedFrom = item.replacedFromName,
        )
    }

    fun tone(kind: ChangeKind): ChangeTone = when (kind) {
        ChangeKind.LOAD_UP, ChangeKind.MORE_REPS, ChangeKind.MORE_TIME, ChangeKind.EXTRA_SET, ChangeKind.HARDER_VARIATION -> ChangeTone.UP
        ChangeKind.LOAD_DOWN, ChangeKind.FEWER_REPS, ChangeKind.LESS_TIME, ChangeKind.FEWER_SETS, ChangeKind.EASIER_VARIATION,
        ChangeKind.RETURN_EASE -> ChangeTone.DOWN
        ChangeKind.HOLD -> ChangeTone.SAME
        ChangeKind.FIRST_TIME -> ChangeTone.INFO
    }
}
