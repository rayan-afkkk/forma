package app.forma.presentation.you

import app.forma.core.domain.DevelopmentBilling
import app.forma.core.domain.PlanningService
import app.forma.core.engine.EquipmentLoads
import app.forma.core.model.AppSettings
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.ProFeature
import app.forma.core.model.ThemeMode
import app.forma.core.model.Tier
import app.forma.core.model.UserProfile
import app.forma.core.model.WeightUnit
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Effect
import app.forma.presentation.Format
import app.forma.presentation.StateHolder
import app.forma.presentation.equipment.EquipmentDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ------------------------------------------------------------------------------------------ You

data class YouUiState(
    val loading: Boolean = true,
    val name: String = "",
    val initials: String = "",
    val tier: String = "Free",
    val tierDetail: String = "",
    val goalSummary: String = "",
    val scheduleSummary: String = "",
    val equipmentSummary: String = "",
    val exclusionsSummary: String = "",
    val soundSummary: String = "",
    val remindersSummary: String = "",
    val settings: AppSettings = AppSettings(),
    val version: String = "",
    val showDeveloperOptions: Boolean = false,
    val developerTier: Tier? = null,
    val confirmDelete: Boolean = false,
)

class YouStateHolder(scope: CoroutineScope, private val services: AppServices) : StateHolder<YouUiState>(scope, YouUiState()) {
    private val devBilling = services.billing as? DevelopmentBilling

    init {
        launch {
            combine(services.planning.snapshots, services.billing.entitlement) { s, entitlement -> s to entitlement }
                .collect { (s, entitlement) ->
                    val profile = s.user.profile
                    val equipment = services.planning.activeEquipment(s)
                    val reminders = s.user.settings.reminders
                    set(
                        YouUiState(
                            loading = false,
                            name = profile.name.ifBlank { "You" },
                            initials = Format.initials(profile.name),
                            tier = if (entitlement.isPro) "Pro" else "Free",
                            tierDetail = when {
                                entitlement.isPro && services.billing.isDevelopment -> "Development entitlement · not a real purchase"
                                entitlement.isPro -> "Managed through Google Play"
                                entitlement.pendingPurchase -> "Purchase pending payment"
                                s.access.sampleRemaining > 0 -> "${Format.count(s.access.sampleRemaining, "adaptive session")} left in your free sample"
                                else -> "Starter program, logging, history and export included"
                            },
                            goalSummary = "${profile.goal.label} · ${profile.experience.label}",
                            scheduleSummary = "${Format.days(s.user.schedule.days)} · ${profile.sessionMinutes} min",
                            equipmentSummary = "${equipment.name} · ${EquipmentLoads.describe(equipment)}",
                            exclusionsSummary = if (s.exclusions.isEmpty()) "None" else Format.count(s.exclusions.size, "exercise") + " excluded",
                            soundSummary = listOf(
                                if (s.user.settings.sound) "Sound on" else "Sound off",
                                if (s.user.settings.haptics) "vibration on" else "vibration off",
                            ).joinToString(", "),
                            remindersSummary = if (reminders.enabled) "${Format.timeOfDay(reminders.minuteOfDay)} on planned days" else "Off",
                            settings = s.user.settings,
                            version = "Version ${services.info.versionName}",
                            showDeveloperOptions = services.info.isDebugBuild && devBilling != null,
                            developerTier = if (devBilling != null) entitlement.tier else null,
                            confirmDelete = current.confirmDelete,
                        ),
                    )
                }
        }
    }

    fun setTheme(theme: ThemeMode) = launch { services.repos.userState.update { it.copy(settings = it.settings.copy(theme = theme)) } }

    /** Changes how weights are displayed. Stored performance is never converted. */
    fun setUnit(unit: WeightUnit) = launch { services.repos.userState.update { it.copy(settings = it.settings.copy(displayUnit = unit)) } }

    fun open(destination: Destination) = navigate(destination)

    fun setDeveloperTier(tier: Tier) {
        devBilling?.setTier(tier)
    }

    fun resetSample() = launch { services.repos.userState.update { it.copy(sampleSessionsUsed = 0, upgradeOfferSeen = false) } }

    fun askDelete() = update { it.copy(confirmDelete = true) }
    fun cancelDelete() = update { it.copy(confirmDelete = false) }

    fun deleteAll() = launch {
        update { it.copy(confirmDelete = false) }
        services.reset.deleteAll()
        services.reminders.update(app.forma.core.model.ReminderSettings(enabled = false), app.forma.core.model.Schedule())
        navigate(Destination.Onboarding, clear = true)
    }

    fun email() {
        val address = services.info.supportEmail
        if (address.isNotBlank()) emit(Effect.OpenUrl("mailto:$address"))
    }
}

// ------------------------------------------------------------------------------------------ sound & haptics

class SettingsStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<AppSettings>(scope, AppSettings()) {
    init {
        launch { services.repos.userState.state.collect { set(it.settings) } }
    }

    private fun change(transform: (AppSettings) -> AppSettings) =
        launch { services.repos.userState.update { it.copy(settings = transform(it.settings)) } }

    fun setSound(on: Boolean) = change { it.copy(sound = on) }
    fun setHaptics(on: Boolean) = change { it.copy(haptics = on) }
    fun setKeepScreenOn(on: Boolean) = change { it.copy(keepScreenOn = on) }
}

// ------------------------------------------------------------------------------------------ goals

data class GoalsUiState(
    val profile: UserProfile = UserProfile(),
    val loading: Boolean = true,
)

class GoalsStateHolder(scope: CoroutineScope, private val services: AppServices) : StateHolder<GoalsUiState>(scope, GoalsUiState()) {
    init {
        launch { services.repos.userState.state.collect { set(GoalsUiState(it.profile, loading = false)) } }
    }

    private fun change(transform: (UserProfile) -> UserProfile) =
        launch { services.repos.userState.update { it.copy(profile = transform(it.profile)) } }

    fun setName(name: String) = change { it.copy(name = name.take(40)) }
    fun setGoal(goal: Goal) = change { it.copy(goal = goal) }
    fun setExperience(level: ExperienceLevel) = change { it.copy(experience = level) }
    fun setMinutes(minutes: Int) = change { it.copy(sessionMinutes = minutes.coerceIn(5, 120)) }
    fun setQuiet(quiet: Boolean) = change { it.copy(quiet = quiet) }
    fun setLimitedSpace(limited: Boolean) = change { it.copy(limitedSpace = limited) }
}

// ------------------------------------------------------------------------------------------ schedule & reminders

data class ScheduleUiState(
    val loading: Boolean = true,
    val days: Set<Int> = emptySet(),
    val remindersEnabled: Boolean = false,
    val reminderMinute: Int = 18 * 60,
    val error: String? = null,
)

class ScheduleStateHolder(scope: CoroutineScope, private val services: AppServices) : StateHolder<ScheduleUiState>(scope, ScheduleUiState()) {
    init {
        launch {
            services.repos.userState.state.collect {
                update { s ->
                    s.copy(
                        loading = false,
                        days = it.schedule.days,
                        remindersEnabled = it.settings.reminders.enabled,
                        reminderMinute = it.settings.reminders.minuteOfDay,
                    )
                }
            }
        }
    }

    fun toggleDay(day: Int) {
        val days = current.days
        val next = if (day in days) days - day else days + day
        if (next.isEmpty()) {
            update { it.copy(error = "Keep at least one training day. You can always rest on a planned day.") }
            return
        }
        update { it.copy(error = null) }
        launch {
            val user = services.repos.userState.update { it.copy(schedule = it.schedule.copy(days = next)) }
            services.reminders.update(user.settings.reminders, user.schedule)
        }
    }

    /**
     * Turns reminders on or off. The platform asks for notification permission first (with an
     * explanation) and only calls this with true when permission was granted.
     */
    fun setReminders(enabled: Boolean) = launch {
        val user = services.repos.userState.update {
            it.copy(settings = it.settings.copy(reminders = it.settings.reminders.copy(enabled = enabled)))
        }
        services.reminders.update(user.settings.reminders, user.schedule)
    }

    fun setReminderTime(minuteOfDay: Int) = launch {
        val user = services.repos.userState.update {
            it.copy(settings = it.settings.copy(reminders = it.settings.reminders.copy(minuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1))))
        }
        services.reminders.update(user.settings.reminders, user.schedule)
    }

    fun permissionDenied() = update {
        it.copy(error = "Notifications are off for Forma, so reminders can't be shown. You can allow them in your phone's settings.")
    }
}

// ------------------------------------------------------------------------------------------ equipment

data class EquipmentRowUi(val id: String, val name: String, val summary: String, val active: Boolean)

data class EquipmentListUiState(
    val loading: Boolean = true,
    val profiles: List<EquipmentRowUi> = emptyList(),
    val canAdd: Boolean = false,
    val addLockedReason: String? = null,
)

class EquipmentListStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<EquipmentListUiState>(scope, EquipmentListUiState()) {
    init {
        launch {
            services.planning.snapshots.collect { s ->
                val activeId = services.planning.activeEquipment(s).id
                val canMultiple = s.access.can(ProFeature.MULTIPLE_EQUIPMENT_PROFILES)
                set(
                    EquipmentListUiState(
                        loading = false,
                        profiles = s.equipment.map { EquipmentRowUi(it.id, it.name, EquipmentLoads.describe(it), it.id == activeId) },
                        canAdd = canMultiple || s.equipment.isEmpty(),
                        addLockedReason = if (canMultiple || s.equipment.isEmpty()) null
                        else "Multiple equipment profiles, such as Home and Travel, are part of Pro.",
                    ),
                )
            }
        }
    }

    fun select(id: String) = launch {
        services.repos.userState.update { it.copy(profile = it.profile.copy(activeEquipmentProfileId = id)) }
    }

    fun edit(id: String) = navigate(Destination.EquipmentEditor(id))

    fun add() {
        if (current.canAdd) navigate(Destination.EquipmentEditor(null)) else navigate(Destination.Paywall("equipment"))
    }
}

data class EquipmentEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val draft: EquipmentDraft = EquipmentDraft(),
    val canDelete: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val errors: List<String> get() = draft.errors()
    val canSave: Boolean get() = errors.isEmpty() && name.isNotBlank()
}

class EquipmentEditorStateHolder(
    scope: CoroutineScope,
    private val services: AppServices,
    private val profileId: String?,
) : StateHolder<EquipmentEditorUiState>(scope, EquipmentEditorUiState()) {
    init {
        launch {
            val all = services.repos.equipment.all()
            val profile = profileId?.let { id -> all.firstOrNull { it.id == id } }
            val unit = services.repos.userState.current().settings.displayUnit
            set(
                if (profile != null) {
                    EquipmentEditorUiState(false, false, profile.name, EquipmentDraft.from(profile), canDelete = all.size > 1)
                } else {
                    EquipmentEditorUiState(false, true, if (all.isEmpty()) "Home" else "Travel", EquipmentDraft(unit = unit), canDelete = false)
                },
            )
        }
    }

    fun setName(name: String) = update { it.copy(name = name.take(30)) }
    fun setDraft(draft: EquipmentDraft) = update { it.copy(draft = draft) }

    fun save() = launch {
        val s = current
        val id = profileId ?: services.planning.let { "profile-${services.clock.nowMillis()}" }
        val profile = s.draft.toProfile(id, s.name.trim()) ?: return@launch
        services.repos.equipment.upsert(profile)
        if (services.repos.equipment.all().size == 1) {
            services.repos.userState.update { it.copy(profile = it.profile.copy(activeEquipmentProfileId = id)) }
        }
        message("Equipment saved. Future workouts use what you have.")
        emit(Effect.Back)
    }

    fun askDelete() = update { it.copy(confirmDelete = true) }
    fun cancelDelete() = update { it.copy(confirmDelete = false) }

    fun delete() = launch {
        val id = profileId ?: return@launch
        val remaining = services.repos.equipment.all().filter { it.id != id }
        if (remaining.isEmpty()) return@launch
        services.repos.equipment.delete(id)
        services.repos.userState.update {
            if (it.profile.activeEquipmentProfileId == id) it.copy(profile = it.profile.copy(activeEquipmentProfileId = remaining.first().id)) else it
        }
        emit(Effect.Back)
    }
}

// ------------------------------------------------------------------------------------------ exclusions

data class ExclusionRowUi(val exerciseId: String, val name: String, val detail: String)

data class ExclusionsUiState(
    val loading: Boolean = true,
    val excluded: List<ExclusionRowUi> = emptyList(),
    val available: List<ExclusionRowUi> = emptyList(),
    val browsing: Boolean = false,
)

class ExclusionsStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<ExclusionsUiState>(scope, ExclusionsUiState()) {
    init {
        launch {
            services.repos.exclusions.exclusions.collect { list ->
                val zone = services.clock.zone()
                val ids = list.map { it.exerciseId }.toSet()
                update {
                    it.copy(
                        loading = false,
                        excluded = list.sortedByDescending { e -> e.createdAt }.mapNotNull { e ->
                            services.catalog.exercise(e.exerciseId)?.let { ex ->
                                ExclusionRowUi(ex.id, ex.name, "Excluded ${Format.shortDate(date(e.createdAt, zone))}")
                            }
                        },
                        available = services.catalog.exercises.filter { ex -> ex.id !in ids }.sortedBy { ex -> ex.name }
                            .map { ex -> ExclusionRowUi(ex.id, ex.name, ex.pattern.label) },
                    )
                }
            }
        }
    }

    private fun date(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun restore(id: String) = launch {
        services.planning.restore(id)
        message("Restored. It can appear in future workouts again.")
    }

    fun exclude(id: String) = launch { services.planning.exclude(id) }

    fun setBrowsing(browsing: Boolean) = update { it.copy(browsing = browsing) }

    companion object {
        fun describe(planning: PlanningService) = planning.catalog.exercises.size
    }
}
