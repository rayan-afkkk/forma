package app.forma.ui.screens.you

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forma.core.model.NoteKind
import app.forma.presentation.NoteUi
import app.forma.presentation.you.BackupStateHolder
import app.forma.presentation.you.MembershipStateHolder
import app.forma.presentation.you.MembershipUiState
import app.forma.presentation.you.OfferUi
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.components.ConfirmDialog
import app.forma.ui.components.Divider
import app.forma.ui.components.FormaCard
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.QuietButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.SettingsGroup
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.TopBar
import app.forma.ui.icons.FormaIcons
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberFileActions
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout
import app.forma.ui.theme.Shapes

// ------------------------------------------------------------------------------------------ membership / paywall

@Composable
fun MembershipRoute(paywall: Boolean) {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("membership") { MembershipStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    MembershipScreen(state, holder, paywall)
}

/**
 * Paywall and membership. Same calm design as the rest of the app: concrete benefits, the full
 * amount charged, renewal terms, and a clear way to dismiss, restore and manage.
 */
@Composable
fun MembershipScreen(state: MembershipUiState, holder: MembershipStateHolder, paywall: Boolean) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(onBack = holder::dismiss, backIcon = if (paywall) FormaIcons.Close else FormaIcons.Back, backDescription = if (paywall) "Not now" else "Back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Layout.screenPadding).padding(bottom = 24.dp)) {
            ProBadge()
            Spacer(Modifier.height(10.dp))
            Text("Forma Pro", style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(state.statusText, style = Forma.type.body, color = Forma.colors.textSecondary)
            state.developmentNotice?.let {
                Spacer(Modifier.height(12.dp))
                NoteCard(NoteUi(NoteKind.INFO, it))
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Pro adds")
            Spacer(Modifier.height(8.dp))
            FormaCard(padding = 16.dp) {
                state.benefits.forEachIndexed { i, b ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(FormaIcons.Check, contentDescription = null, tint = Forma.colors.accentText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(b.title, style = Forma.type.bodyStrong, color = Forma.colors.text)
                            Text(b.detail, style = Forma.type.supporting, color = Forma.colors.textSecondary)
                        }
                    }
                }
            }

            if (!state.isPro) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Choose a plan")
                Spacer(Modifier.height(8.dp))
                when {
                    state.loading -> Text("Loading prices from Google Play…", style = Forma.type.supporting, color = Forma.colors.textMuted)
                    state.unavailable != null -> NoteCard(NoteUi(NoteKind.INFO, state.unavailable!!))
                    else -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.offers.forEach { OfferCard(it, it.id == state.selectedOfferId) { holder.select(it.id) } }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Always free")
            Spacer(Modifier.height(8.dp))
            state.freeIncludes.forEach {
                Text("• $it", style = Forma.type.supporting, color = Forma.colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Subscriptions renew automatically at the price shown until cancelled. Cancel any time in Google Play at least 24 hours before renewal to avoid the next charge. " +
                    "Payment is charged to your Google account. Workouts are never interrupted if a subscription ends; your history stays available.",
                style = Forma.type.caption, color = Forma.colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuietButton("Restore purchases", holder::restore, color = Forma.colors.textSecondary)
                QuietButton("Manage subscription", holder::manage, icon = FormaIcons.External, color = Forma.colors.textSecondary)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = Layout.screenPadding, vertical = 12.dp)) {
            if (!state.isPro && state.offers.isNotEmpty()) {
                val offer = state.offers.firstOrNull { it.id == state.selectedOfferId }
                PrimaryButton(
                    if (offer != null) "Continue with ${offer.title.lowercase()} · ${offer.price}" else "Continue",
                    holder::purchase,
                    busy = state.purchasing,
                    enabled = offer != null && !state.pending,
                )
            }
            QuietButton(if (paywall && !state.isPro) "Not now" else "Close", holder::dismiss, Modifier.align(Alignment.CenterHorizontally), color = Forma.colors.textSecondary)
        }
    }
}

@Composable
private fun OfferCard(offer: OfferUi, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Forma.colors.text else Forma.colors.border, Shapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(offer.title, style = Forma.type.cardTitleSmall, color = Forma.colors.text, modifier = Modifier.weight(1f))
            Text(offer.price, style = Forma.type.numberMedium, color = Forma.colors.text)
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(FormaIcons.Check, contentDescription = null, tint = Forma.colors.text, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(offer.terms, style = Forma.type.supporting, color = Forma.colors.textSecondary)
        if (offer.placeholder) Text("Development price, not loaded from Google Play.", style = Forma.type.caption, color = Forma.colors.textMuted)
    }
}

// ------------------------------------------------------------------------------------------ backup

@Composable
fun BackupRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("backup") { BackupStateHolder(it, services) }
    val state by holder.state.collectUiState()
    val files = rememberFileActions(onSaved = holder::saved, onRead = { text -> if (text == null) holder.readFailed() else holder.fileRead(text) })
    HandleEffects(holder, onSaveFile = { files.save(it.suggestedName, it.mimeType, it.content) }, onPickImport = files::pickBackup)
    SettingsScaffold("Backup and export", "Your data lives on this device. Save a copy somewhere you choose, or bring one back.") {
        SettingsGroup {
            SettingsRow("Export a backup", holder::exportJson, icon = FormaIcons.Export, description = "Everything you've created, as a JSON file you can import later.")
            Divider()
            SettingsRow("Export sets as a spreadsheet", holder::exportCsv, icon = FormaIcons.Note, description = "Every logged set as CSV, with weights in the units you logged them.")
            Divider()
            SettingsRow("Import a backup", holder::chooseImport, icon = FormaIcons.Import, description = "Replaces the data on this device with the backup's contents.")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Files are saved and opened through Android's file picker, so the app never needs access to your other files. Purchases are restored from Google Play, not from backups.",
            style = Forma.type.caption, color = Forma.colors.textMuted,
        )
        state.lastResult?.let {
            Spacer(Modifier.height(16.dp))
            NoteCard(NoteUi(NoteKind.INFO, it))
        }
    }
    if (state.confirmImport != null) {
        ConfirmDialog(
            title = "Replace data on this device?",
            body = "Importing replaces your profile, equipment, exclusions, schedule, settings and workout history with the contents of the backup. Export a backup of the current data first if you want to keep it.",
            confirmLabel = "Import",
            onConfirm = holder::confirmImport,
            onDismiss = holder::cancelImport,
            destructive = true,
        )
    }
}

// ------------------------------------------------------------------------------------------ help & privacy

@Composable
private fun InfoBlock(title: String, body: String) {
    Text(title, style = Forma.type.cardTitleSmall, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(body, style = Forma.type.body, color = Forma.colors.textSecondary)
    Spacer(Modifier.height(20.dp))
}

@Composable
fun HelpScreen() {
    val services = LocalAppServices.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    SettingsScaffold("Help and feedback") {
        InfoBlock("How is my workout chosen?", "Each session follows your program and fills it with exercises that match your equipment, experience, space and exclusions. After you log a session, the next one adjusts to what you actually did and how it felt.")
        InfoBlock("Replace, make easier or exclude?", "Replace and make easier change today's workout only. Exclude permanently removes an exercise from every future workout until you restore it in You › Excluded exercises.")
        InfoBlock("I missed a session", "Nothing piles up. Your next planned day simply has the next session. After a longer break, Forma will ask whether you'd like to ease back in.")
        InfoBlock("Is this medical advice?", "No. Forma offers general fitness guidance. Stop if you feel pain, dizziness or unusual shortness of breath, and speak to a qualified health professional about injuries or health conditions.")
        if (services.info.supportEmail.isNotBlank()) {
            SettingsRow("Email us", { runCatching { uriHandler.openUri("mailto:${services.info.supportEmail}") } }, icon = FormaIcons.External, description = services.info.supportEmail)
        } else {
            NoteCard(NoteUi(NoteKind.INFO, "Feedback contact details will be added before release."))
        }
        Spacer(Modifier.height(28.dp))
        InfoBlock(
            "Open-source notices",
            "Fonts: Newsreader (© The Newsreader Project Authors) and Inter (© The Inter Project Authors), " +
                "both under the SIL Open Font License 1.1. " +
                "Software: Kotlin, kotlinx libraries and Android Jetpack libraries under the Apache License 2.0. " +
                "Google Play Billing Library under the Android Software Development Kit License. " +
                "Version ${services.info.versionName}.",
        )
    }
}

@Composable
fun PrivacyScreen() {
    SettingsScaffold("Privacy", "A plain summary of how this version handles your data.") {
        InfoBlock("No account", "You don't need to sign up. Your name is optional and stays on this device.")
        InfoBlock("Stored on your device", "Your profile, equipment, schedule, exclusions, notes and workout history are stored only on this device. This version has no server and sends none of it anywhere.")
        InfoBlock("No ads or analytics", "This version contains no advertising and no analytics services.")
        InfoBlock("Purchases", "Subscriptions are handled by Google Play. Forma receives purchase status from Google Play but never your payment details.")
        InfoBlock("Notifications", "Reminders are optional and only used if you turn them on.")
        InfoBlock("Your control", "Export your data at any time from Backup and export, and delete it all from You › Delete local data. Uninstalling the app also removes it. Android may include app data in your device backup, which you control in your phone's settings.")
        NoteCard(NoteUi(NoteKind.INFO, "Draft summary. The full privacy policy, publisher details and legal review are still required before public release."))
    }
}
