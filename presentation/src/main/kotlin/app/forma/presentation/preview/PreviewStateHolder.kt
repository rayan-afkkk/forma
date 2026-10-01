package app.forma.presentation.preview

import app.forma.core.domain.PlanKey
import app.forma.core.domain.StartResult
import app.forma.core.engine.EasierOption
import app.forma.core.engine.ReplacementResult
import app.forma.core.model.Access
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.PlanMapper
import app.forma.presentation.PlanUi
import app.forma.presentation.StateHolder
import kotlinx.coroutines.CoroutineScope

data class OptionUi(val exerciseId: String, val name: String, val reason: String, val relation: String, val summary: String)

sealed interface PreviewSheet {
    /** Exercise details with demonstration, cues and actions. */
    data class Item(val key: String, val easier: String?, val easierUnavailable: String?) : PreviewSheet
    data class Replace(val key: String, val options: List<OptionUi>, val none: String?) : PreviewSheet
    data class ConfirmExclude(val key: String, val name: String) : PreviewSheet
}

data class PreviewUiState(
    val loading: Boolean = true,
    val plan: PlanUi? = null,
    val missing: Boolean = false,
    val sheet: PreviewSheet? = null,
    val starting: Boolean = false,
    val inProgressSessionId: String? = null,
)

/** A workout before it starts: what it contains, why, and per-exercise adjustments for today. */
class PreviewStateHolder(
    scope: CoroutineScope,
    private val services: AppServices,
    planKey: String,
) : StateHolder<PreviewUiState>(scope, PreviewUiState()) {

    val key: PlanKey = PlanKey.parse(planKey)
    private val planning = services.planning

    init {
        launch {
            planning.observePlan(key).collect { generated ->
                val s = planning.snapshot()
                val locked = when (key) {
                    is PlanKey.Template -> planning.catalog.sessionTemplate(key.id)?.let { !s.access.canUse(planning.accessFor(it)) } ?: false
                    PlanKey.ProgramNext -> false
                }
                update {
                    it.copy(
                        loading = false,
                        missing = generated == null,
                        plan = generated?.let { g -> PlanMapper.toUi(g, services.catalog, key.value, locked) },
                        inProgressSessionId = s.inProgress?.id,
                    )
                }
            }
        }
    }

    fun openItem(itemKey: String) = launch {
        val option = planning.easierOption(key, itemKey)
        update {
            it.copy(
                sheet = PreviewSheet.Item(
                    key = itemKey,
                    easier = option?.takeUnless { o -> o is EasierOption.None }?.description,
                    easierUnavailable = (option as? EasierOption.None)?.description,
                ),
            )
        }
    }

    fun closeSheet() = update { it.copy(sheet = null) }

    fun showReplacements(itemKey: String) = launch {
        val result = planning.replacementOptions(key, itemKey) ?: return@launch
        update {
            it.copy(
                sheet = when (result) {
                    is ReplacementResult.Available -> PreviewSheet.Replace(
                        itemKey,
                        result.options.map { o ->
                            OptionUi(o.exercise.id, o.exercise.name, o.reason, o.relation.label, o.exercise.summary)
                        },
                        none = null,
                    )
                    is ReplacementResult.None -> PreviewSheet.Replace(itemKey, emptyList(), result.explanation)
                },
            )
        }
    }

    /** Replace for this workout only. Permanent preferences are unchanged. */
    fun replace(itemKey: String, exerciseId: String) = launch {
        planning.replaceForToday(key, itemKey, exerciseId)
        update { it.copy(sheet = null) }
        message("Replaced for this workout only.")
    }

    fun makeEasier(itemKey: String) = launch {
        planning.makeEasierForToday(key, itemKey)
        update { it.copy(sheet = null) }
        message("Made easier for today.")
    }

    fun omit(itemKey: String) = launch {
        planning.omitForToday(key, itemKey)
        update { it.copy(sheet = null) }
        message("Left out of today's workout.")
    }

    fun askExclude(itemKey: String) {
        val name = current.plan?.items?.firstOrNull { it.key == itemKey }?.name ?: return
        update { it.copy(sheet = PreviewSheet.ConfirmExclude(itemKey, name)) }
    }

    /** Permanent exclusion: this exercise will not appear in future workouts until restored in You. */
    fun exclude(itemKey: String) = launch {
        val exerciseId = planning.currentPlan(key)?.plan?.item(itemKey)?.exerciseId ?: return@launch
        planning.exclude(exerciseId)
        update { it.copy(sheet = null) }
        message("Excluded. You can restore it any time in You › Excluded exercises.")
    }

    fun resetChanges() = launch {
        planning.resetToday(key)
        message("Today's changes were undone.")
    }

    fun start() {
        if (current.starting) return
        update { it.copy(starting = true) }
        launch {
            val result = services.sessions.start(key)
            update { it.copy(starting = false) }
            when (result) {
                is StartResult.Started -> navigate(Destination.Player(result.sessionId))
                is StartResult.AlreadyActive -> {
                    update { it.copy(inProgressSessionId = result.sessionId) }
                    message("Another session is in progress. Resume or finish it first.")
                }
                StartResult.NeedsPro -> navigate(Destination.Paywall("session"))
                is StartResult.NothingToDo -> message(result.reason)
            }
        }
    }

    fun resumeInProgress() {
        val id = current.inProgressSessionId ?: return
        navigate(Destination.Player(id))
    }

    fun unlock() = navigate(Destination.Paywall("session"))

    companion object {
        fun isPro(access: Access) = access == Access.PRO
    }
}
