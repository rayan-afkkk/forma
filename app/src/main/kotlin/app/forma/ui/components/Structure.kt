package app.forma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.forma.core.model.MovementPattern
import app.forma.presentation.Tab
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.illustrations.PoseFigure
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout
import app.forma.ui.theme.Shapes
import app.forma.ui.theme.maxScale

/** Large serif page title in the editorial style, with optional eyebrow and supporting line. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        if (eyebrow != null) {
            Text(eyebrow, style = Forma.type.supporting, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = Forma.type.pageTitle, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = Forma.type.body, color = Forma.colors.textSecondary)
        }
    }
}

/** Minimal top bar: back button, optional title and trailing actions. */
@Composable
fun TopBar(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    backIcon: androidx.compose.ui.graphics.vector.ImageVector = FormaIcons.Back,
    backDescription: String = "Back",
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) RoundIconButton(backIcon, backDescription, onBack) else Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = Forma.type.bodyStrong, color = Forma.colors.text, modifier = Modifier.semantics { heading() })
            }
        }
        actions()
    }
}

/** A scrolling screen with standard padding and room at the bottom for a sticky action. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    bottomSpace: androidx.compose.ui.unit.Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Layout.screenPadding)
            .padding(bottom = bottomSpace),
        content = content,
    )
}

/** Simmr-inspired empty state: an outline illustration, a large serif message, one line, one action. */
@Composable
fun EmptyState(
    illustration: Illustration,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FormaIllustration(illustration, width = 180.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = Forma.type.sectionTitle, color = Forma.colors.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        Text(body, style = Forma.type.body, color = Forma.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** Styled bottom sheet with a drag handle. Content scrolls if it does not fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaBottomSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Forma.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        shape = Shapes.sheet,
        containerColor = colors.surface,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(Shapes.pill).background(colors.borderStrong))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Layout.screenPadding)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            if (title != null) {
                Text(title, style = Forma.type.sectionTitle, color = colors.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/** Confirmation dialog. Destructive confirmations name exactly what will be removed. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val colors = Forma.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { QuietButton(confirmLabel, onConfirm, color = if (destructive) colors.danger else colors.text) },
        dismissButton = { QuietButton(dismissLabel, onDismiss, color = colors.textSecondary) },
        title = { Text(title, style = Forma.type.cardTitle, color = colors.text) },
        text = { Text(body, style = Forma.type.body, color = colors.textSecondary) },
        containerColor = colors.surface,
        shape = RoundedCornerShape(26.dp),
    )
}

@Composable
fun FormaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data: SnackbarData ->
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Forma.colors.primary)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(data.visuals.message, style = Forma.type.supporting, color = Forma.colors.onPrimary)
        }
    }
}

fun tabIcon(tab: Tab) = when (tab) {
    Tab.TODAY -> FormaIcons.Today
    Tab.EXPLORE -> FormaIcons.Explore
    Tab.PROGRESS -> FormaIcons.Progress
    Tab.YOU -> FormaIcons.You
}

/** Restrained bottom navigation: background, thin top divider, muted inactive and cream active items. */
@Composable
fun FormaBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = Forma.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) colors.text else colors.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tabIcon(tab), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        style = Forma.type.labelSmall.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium).maxScale(1.3f),
                        color = tint,
                        maxLines = 1,
                    )
                    // A small bar under the active tab so selection is not shown by colour alone.
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(width = 16.dp, height = 2.dp).clip(Shapes.pill).background(if (isSelected) colors.text else colors.background))
                }
            }
        }
    }
}

/**
 * Demonstration area. Until licensed demonstration media exists, it shows an original line figure
 * in a pose that matches the movement and says plainly that it is a development placeholder.
 */
@Composable
fun DemonstrationArea(pattern: MovementPattern, exerciseName: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = Forma.colors
    Box(
        modifier
            .fillMaxWidth()
            .let { if (compact) it.height(150.dp) else it.aspectRatio(16f / 10f) }
            .clip(Shapes.card)
            .background(colors.surfaceSecondary)
            .border(1.dp, colors.border, Shapes.card)
            .semantics(mergeDescendants = true) {
                contentDescription = "Demonstration placeholder for $exerciseName. Final demonstration media is not available yet; follow the technique cues."
            },
    ) {
        PoseFigure(pattern, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp))
        Text(
            "Development placeholder",
            style = Forma.type.caption.maxScale(1.3f),
            color = colors.textMuted,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
    }
}

/** Centered loading text; the app avoids spinners that animate continuously during workouts. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        Text("Loading…", style = Forma.type.supporting, color = Forma.colors.textMuted)
    }
}

@Composable
fun MissingState(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        FormaIllustration(Illustration.SEARCH, width = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, style = Forma.type.body, color = Forma.colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Go back", onBack)
    }
}
