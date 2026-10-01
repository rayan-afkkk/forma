package app.forma.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

@Serializable
data class UserProfile(
    val name: String = "",
    val goal: Goal = Goal.GENERAL_FITNESS,
    val experience: ExperienceLevel = ExperienceLevel.NEW,
    val sessionMinutes: Int = 25,
    /** Avoid jumping and other loud movements. Treated as a hard constraint. */
    val quiet: Boolean = false,
    /** Avoid exercises that need room to step or travel. Treated as a hard constraint. */
    val limitedSpace: Boolean = false,
    val activeEquipmentProfileId: String = "",
)

/** ISO day numbers: 1 = Monday ... 7 = Sunday. */
@Serializable
data class Schedule(
    val days: Set<Int> = setOf(1, 3, 5),
) {
    init {
        require(days.all { it in 1..7 }) { "Days must be ISO day numbers" }
    }

    val sessionsPerWeek: Int get() = days.size
}

/** Moves one planned day to another. Stored by local date (yyyy-MM-dd). */
@Serializable
data class ScheduleMove(val fromDate: String, val toDate: String)

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    /** Minutes after local midnight. */
    val minuteOfDay: Int = 18 * 60,
)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Unit for new equipment and for history display. Stored loads keep their own unit. */
    val displayUnit: WeightUnit = WeightUnit.KG,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val reminders: ReminderSettings = ReminderSettings(),
)

/** Choice made at a return check-in after a break. */
@Serializable
data class ReturnPlan(
    /** Local date the check-in was answered. */
    val answeredOn: String,
    val easeBack: Boolean,
    /** Sessions left that use the reduced return prescription. */
    val sessionsRemaining: Int,
)

@Serializable
data class UserState(
    val onboardingComplete: Boolean = false,
    val safetyNoteAcknowledged: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val schedule: Schedule = Schedule(),
    val settings: AppSettings = AppSettings(),
    val returnPlan: ReturnPlan? = null,
    /** Free adaptive sessions completed (the sample). Tracked locally; see docs/MONETIZATION.md. */
    val sampleSessionsUsed: Int = 0,
    val upgradeOfferSeen: Boolean = false,
)
