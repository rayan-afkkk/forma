package app.forma.presentation.explore

import app.forma.core.domain.EnrollResult
import app.forma.core.domain.ExploreModel
import app.forma.core.domain.PlanKey
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.ExploreCategory
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Format
import app.forma.presentation.StateHolder
import app.forma.presentation.Tab
import kotlinx.coroutines.CoroutineScope

enum class ExploreFilter(val label: String) {
    FAVORITES("Favorites"),
    SHORT("Up to 15 min"),
    MEDIUM("15–30 min"),
    BODYWEIGHT("No equipment"),
    DUMBBELLS("Dumbbells"),
    FREE("Free"),
}

data class WorkoutCardUi(
    val templateId: String,
    val name: String,
    val description: String,
    val duration: String,
    val minutes: Int,
    val equipment: String,
    val difficulty: String,
    val programName: String?,
    val categories: Set<ExploreCategory>,
    val locked: Boolean,
    val favorite: Boolean,
)

data class ProgramCardUi(
    val id: String,
    val name: String,
    val summary: String,
    val detail: String,
    val enrolled: Boolean,
    val locked: Boolean,
    val unavailableReason: String?,
)

data class CategoryUi(val category: ExploreCategory, val count: Int)

data class ExploreUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filters: Set<ExploreFilter> = emptySet(),
    val category: ExploreCategory? = null,
    val categories: List<CategoryUi> = emptyList(),
    val programs: List<ProgramCardUi> = emptyList(),
    val results: List<WorkoutCardUi> = emptyList(),
    val programSheet: ProgramCardUi? = null,
) {
    val isFiltering: Boolean get() = query.isNotBlank() || filters.isNotEmpty() || category != null
}

class ExploreStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<ExploreUiState>(scope, ExploreUiState()) {

    private var model: ExploreModel? = null

    init {
        launch {
            services.planning.observeExplore().collect {
                model = it
                recompute()
            }
        }
    }

    fun setQuery(query: String) {
        update { it.copy(query = query.take(60)) }
        recompute()
    }

    fun toggleFilter(filter: ExploreFilter) {
        update { it.copy(filters = if (filter in it.filters) it.filters - filter else it.filters + filter) }
        recompute()
    }

    fun selectCategory(category: ExploreCategory?) {
        update { it.copy(category = if (it.category == category) null else category) }
        recompute()
    }

    fun clearFilters() {
        update { it.copy(query = "", filters = emptySet(), category = null) }
        recompute()
    }

    fun toggleFavorite(templateId: String) {
        val favorite = model?.sessions?.firstOrNull { it.template.id == templateId }?.favorite ?: false
        launch { services.planning.setFavorite(templateId, !favorite) }
    }

    fun open(templateId: String) = navigate(Destination.Preview(PlanKey.Template(templateId).value))

    fun openProgram(id: String) = update { it.copy(programSheet = it.programs.firstOrNull { p -> p.id == id }) }
    fun closeProgram() = update { it.copy(programSheet = null) }

    fun enroll(id: String) = launch {
        when (val result = services.planning.enroll(id)) {
            EnrollResult.Enrolled -> {
                update { it.copy(programSheet = null) }
                message("Your plan now follows ${services.catalog.program(id)?.name}.")
                navigate(Destination.Main(Tab.TODAY))
            }
            EnrollResult.NeedsPro -> {
                update { it.copy(programSheet = null) }
                navigate(Destination.Paywall("program"))
            }
            is EnrollResult.Unavailable -> message(result.reason)
        }
    }

    private fun recompute() {
        val m = model ?: return
        val s = current
        val q = s.query.trim().lowercase()
        val all = m.sessions.map { entry ->
            val plan = entry.plan.plan
            WorkoutCardUi(
                templateId = entry.template.id,
                name = entry.template.name,
                description = entry.template.purpose,
                duration = Format.aboutMinutes(plan.estimatedMinutes),
                minutes = plan.estimatedMinutes,
                equipment = plan.equipmentSummary,
                difficulty = when (entry.template.level) {
                    ExperienceLevel.NEW -> "Beginner friendly"
                    ExperienceLevel.SOME -> "Some experience"
                    ExperienceLevel.EXPERIENCED -> "Experienced"
                },
                programName = entry.program?.name,
                categories = entry.template.categories + entry.program?.categories.orEmpty(),
                locked = entry.locked,
                favorite = entry.favorite,
            ) to (plan.items.joinToString(" ") { it.exerciseName } + " " + entry.template.focus).lowercase()
        }
        val filtered = all.filter { (card, searchable) ->
            (q.isEmpty() || card.name.lowercase().contains(q) || card.description.lowercase().contains(q) ||
                card.programName?.lowercase()?.contains(q) == true || searchable.contains(q)) &&
                (s.category == null || s.category in card.categories || categoryMatches(s.category, card)) &&
                s.filters.all { f ->
                    when (f) {
                        ExploreFilter.FAVORITES -> card.favorite
                        ExploreFilter.SHORT -> card.minutes <= 15
                        ExploreFilter.MEDIUM -> card.minutes in 16..30
                        ExploreFilter.BODYWEIGHT -> card.equipment == "No equipment"
                        ExploreFilter.DUMBBELLS -> card.equipment.contains("umbbell")
                        ExploreFilter.FREE -> !card.locked
                    }
                }
        }.map { it.first }
            // Standalone sessions first, then program sessions; unlocked before locked.
            .sortedWith(compareBy<WorkoutCardUi>({ it.locked }, { it.programName != null }, { it.minutes }))

        val categories = ExploreCategory.entries.map { c ->
            CategoryUi(c, all.count { (card, _) -> c in card.categories || categoryMatches(c, card) })
        }
        val programs = m.programs.map { p ->
            ProgramCardUi(
                id = p.program.id,
                name = p.program.name,
                summary = p.program.summary,
                detail = "${Format.count(p.program.rotation.size, "session")} in rotation · " +
                    "${p.program.minSessionsPerWeek}–${p.program.maxSessionsPerWeek} per week",
                enrolled = p.enrolled,
                locked = p.locked,
                unavailableReason = p.unavailableReason,
            )
        }
        update { it.copy(loading = false, results = filtered, categories = categories, programs = programs) }
    }

    private fun categoryMatches(category: ExploreCategory, card: WorkoutCardUi): Boolean = when (category) {
        ExploreCategory.SHORT -> card.minutes <= 15
        ExploreCategory.BODYWEIGHT -> card.equipment == "No equipment"
        ExploreCategory.DUMBBELLS -> card.equipment.contains("umbbell")
        else -> false
    }
}
