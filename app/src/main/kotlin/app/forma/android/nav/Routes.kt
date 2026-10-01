package app.forma.android.nav

import app.forma.presentation.Destination
import app.forma.presentation.Tab
import kotlinx.serialization.Serializable

/*
 * Type-safe Navigation Compose routes. Shared UI only knows [Destination]; these routes exist so
 * arguments survive process death in the saved back stack. Both directions are mapped here.
 */

@Serializable data object NavToday
@Serializable data object NavExplore
@Serializable data object NavProgress
@Serializable data object NavYou
@Serializable data object NavOnboarding
@Serializable data class NavPreview(val planKey: String)
@Serializable data class NavPlayer(val sessionId: String)
@Serializable data class NavSummary(val sessionId: String, val justFinished: Boolean)
@Serializable data class NavPaywall(val reason: String)
@Serializable data class NavEquipmentEditor(val profileId: String? = null)

/** Settings and information pages without arguments. */
@Serializable data class NavPage(val name: String)

fun Destination.toRoute(): Any = when (this) {
    is Destination.Main -> tabRoute(tab)
    Destination.Onboarding -> NavOnboarding
    is Destination.Preview -> NavPreview(planKey)
    is Destination.Player -> NavPlayer(sessionId)
    is Destination.Summary -> NavSummary(sessionId, justFinished)
    is Destination.Paywall -> NavPaywall(reason)
    is Destination.EquipmentEditor -> NavEquipmentEditor(profileId)
    Destination.Goals -> NavPage("goals")
    Destination.Schedule -> NavPage("schedule")
    Destination.Equipment -> NavPage("equipment")
    Destination.Exclusions -> NavPage("exclusions")
    Destination.SoundAndHaptics -> NavPage("sound")
    Destination.Membership -> NavPage("membership")
    Destination.Backup -> NavPage("backup")
    Destination.Help -> NavPage("help")
    Destination.Privacy -> NavPage("privacy")
}

fun tabRoute(tab: Tab): Any = when (tab) {
    Tab.TODAY -> NavToday
    Tab.EXPLORE -> NavExplore
    Tab.PROGRESS -> NavProgress
    Tab.YOU -> NavYou
}

/** Unknown names (e.g. from an older saved state) fall back to Help rather than crashing. */
fun NavPage.toDestination(): Destination = when (name) {
    "goals" -> Destination.Goals
    "schedule" -> Destination.Schedule
    "equipment" -> Destination.Equipment
    "exclusions" -> Destination.Exclusions
    "sound" -> Destination.SoundAndHaptics
    "membership" -> Destination.Membership
    "backup" -> Destination.Backup
    "privacy" -> Destination.Privacy
    else -> Destination.Help
}
