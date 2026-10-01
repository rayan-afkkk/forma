package app.forma.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.forma.core.model.ThemeMode
import app.forma.core.model.Tier
import app.forma.core.model.WeightUnit
import app.forma.presentation.Destination
import app.forma.presentation.you.YouStateHolder
import app.forma.presentation.you.YouUiState
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.components.Badge
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaCard
import app.forma.ui.components.LoadingState
import app.forma.ui.components.PageHeader
import app.forma.ui.components.PillSegmentedControl
import app.forma.ui.components.ProBadge
import app.forma.ui.components.ScreenColumn
import app.forma.ui.components.SettingsGroup
import app.forma.ui.components.SettingsRow
import app.forma.ui.icons.FormaIcons
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.theme.Forma

@Composable
fun YouRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("you") { YouStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    YouScreen(state, holder)
}

@Composable
fun YouScreen(state: YouUiState, holder: YouStateHolder) {
    if (state.loading) {
        LoadingState()
        return
    }
    ScreenColumn(Modifier.statusBarsPadding()) {
        PageHeader("You")
        Spacer(Modifier.height(8.dp))

        // Profile card: local name and initials only. There is no account.
        FormaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pastel = Forma.colors.pastels.peach
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(pastel.container).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.initials.isNotEmpty()) Text(state.initials, style = Forma.type.cardTitle, color = pastel.content)
                    else Icon(FormaIcons.You, contentDescription = null, tint = pastel.content)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name, style = Forma.type.cardTitle, color = Forma.colors.text)
                    Spacer(Modifier.height(4.dp))
                    if (state.tier == "Pro") ProBadge() else Badge("Free")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(state.tierDetail, style = Forma.type.supporting, color = Forma.colors.textSecondary)
            Text("Stored on this device only. No account needed.", style = Forma.type.caption, color = Forma.colors.textMuted)
        }

        Spacer(Modifier.height(24.dp))
        SettingsGroup(title = "Training") {
            SettingsRow("Goals and experience", { holder.open(Destination.Goals) }, icon = FormaIcons.Target, description = state.goalSummary)
            Divider()
            SettingsRow("Schedule and reminders", { holder.open(Destination.Schedule) }, icon = FormaIcons.Calendar, description = "${state.scheduleSummary}\nReminders: ${state.remindersSummary}")
            Divider()
            SettingsRow("Equipment", { holder.open(Destination.Equipment) }, icon = FormaIcons.Dumbbell, description = state.equipmentSummary)
            Divider()
            SettingsRow("Excluded exercises", { holder.open(Destination.Exclusions) }, icon = FormaIcons.Close, description = state.exclusionsSummary)
        }

        Spacer(Modifier.height(24.dp))
        SettingsGroup(title = "Preferences") {
            Column(Modifier.padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FormaIcons.Weight, contentDescription = null, tint = Forma.colors.textSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Units", style = Forma.type.bodyStrong, color = Forma.colors.text)
                }
                Spacer(Modifier.height(10.dp))
                PillSegmentedControl(WeightUnit.entries, state.settings.displayUnit, { if (it == WeightUnit.KG) "Kilograms" else "Pounds" }, holder::setUnit)
                Spacer(Modifier.height(6.dp))
                Text("Changes how weights are shown. Logged weights are never converted.", style = Forma.type.caption, color = Forma.colors.textMuted)
            }
            Divider()
            Column(Modifier.padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FormaIcons.Moon, contentDescription = null, tint = Forma.colors.textSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Appearance", style = Forma.type.bodyStrong, color = Forma.colors.text)
                }
                Spacer(Modifier.height(10.dp))
                PillSegmentedControl(ThemeMode.entries, state.settings.theme, { it.label }, holder::setTheme)
            }
            Divider()
            SettingsRow("Sound, vibration and screen", { holder.open(Destination.SoundAndHaptics) }, icon = FormaIcons.Sound, description = state.soundSummary)
        }

        Spacer(Modifier.height(24.dp))
        SettingsGroup(title = "Membership") {
            SettingsRow("Membership", { holder.open(Destination.Membership) }, icon = FormaIcons.Sparkle, description = "${state.tier} · ${state.tierDetail}")
        }

        Spacer(Modifier.height(24.dp))
        SettingsGroup(title = "Your data") {
            SettingsRow("Backup and export", { holder.open(Destination.Backup) }, icon = FormaIcons.Export, description = "Save a copy of your data or restore one.")
            Divider()
            SettingsRow("Privacy", { holder.open(Destination.Privacy) }, icon = FormaIcons.Shield)
            Divider()
            SettingsRow("Help and feedback", { holder.open(Destination.Help) }, icon = FormaIcons.Help)
            Divider()
            SettingsRow("Delete local data", holder::askDelete, icon = FormaIcons.Trash, destructive = true, description = "Remove everything stored on this device.")
        }

        if (state.showDeveloperOptions) {
            Spacer(Modifier.height(24.dp))
            SettingsGroup(title = "Developer options (debug builds only)") {
                Column(Modifier.padding(vertical = 14.dp)) {
                    Text("Entitlement", style = Forma.type.bodyStrong, color = Forma.colors.text)
                    Text("Switches a local test flag. It never unlocks real purchases and is not in release builds.", style = Forma.type.caption, color = Forma.colors.textMuted)
                    Spacer(Modifier.height(10.dp))
                    PillSegmentedControl(Tier.entries, state.developerTier ?: Tier.FREE, { if (it == Tier.PRO) "Pro" else "Free" }, holder::setDeveloperTier)
                }
                Divider()
                SettingsRow("Reset free adaptive sample", holder::resetSample, icon = FormaIcons.Undo)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(state.version, style = Forma.type.caption, color = Forma.colors.textMuted, modifier = Modifier.padding(start = 4.dp))
    }

    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Delete all local data?",
            body = "This permanently removes your profile, equipment, exclusions, favorites, schedule, settings and complete workout history from this device. " +
                "Export a backup first if you might want it later. Subscriptions are managed by Google Play and are not cancelled by this.",
            confirmLabel = "Delete everything",
            onConfirm = holder::deleteAll,
            onDismiss = holder::cancelDelete,
            destructive = true,
        )
    }
}
