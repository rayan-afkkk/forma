package app.forma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.forma.presentation.ChangeTone
import app.forma.presentation.NoteUi
import app.forma.core.model.NoteKind
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Dimens
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Pastel
import app.forma.ui.theme.Shapes

/** Warm charcoal card with a thin border and broad corners. */
@Composable
fun FormaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    color: Color = Forma.colors.surface,
    borderColor: Color = Forma.colors.border,
    padding: androidx.compose.ui.unit.Dp = Dimens.cardPadding,
    fillWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .let { if (fillWidth) it.fillMaxWidth() else it }
            .clip(Shapes.card)
            .background(color)
            .border(1.dp, borderColor, Shapes.card)
            .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** A pastel content card, such as today's workout. Text uses the pastel's own dark content colour. */
@Composable
fun PastelCard(
    pastel: Pastel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bordered = Forma.colors.pastelBorders
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(pastel.container)
            .let { if (bordered) it.border(1.dp, pastel.border, Shapes.card) else it }
            .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
            .padding(Dimens.cardPadding),
        content = content,
    )
}

/**
 * Playful category tile with a subtle "lip" for depth, as in the reference. Selection adds a
 * check mark and a thicker outline, never only a colour change.
 */
@Composable
fun CategoryTile(
    label: String,
    detail: String,
    icon: ImageVector,
    pastel: Pastel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(Shapes.tile)
                .background(pastel.border),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp)
                .clip(Shapes.tile)
                .background(pastel.container)
                .let { if (selected) it.border(2.dp, pastel.content, Shapes.tile) else it }
                .clickable(role = Role.Tab, onClick = onClick)
                .semantics { this.selected = selected }
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(pastel.content.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = pastel.content, modifier = Modifier.size(20.dp))
                }
                if (selected) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(pastel.content), contentAlignment = Alignment.Center) {
                        Icon(FormaIcons.Check, contentDescription = null, tint = pastel.container, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(label, style = Forma.type.cardTitleSmall, color = pastel.content)
            Text(detail, style = Forma.type.caption, color = pastel.secondary)
        }
    }
}

/** Small text badge, e.g. "Pro" or "Free". Includes an icon so it is not distinguished by colour alone. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Forma.colors.text, background: Color = Forma.colors.surfaceSecondary) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Forma.type.labelSmall, color = color)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier, onPastel: Pastel? = null) {
    Badge(
        "Pro",
        modifier,
        icon = FormaIcons.Sparkle,
        color = onPastel?.content ?: Forma.colors.accentText,
        background = onPastel?.content?.copy(alpha = 0.08f) ?: Forma.colors.surfaceSecondary,
    )
}

/** A plain-language explanation, such as why a plan changed or a constraint conflict. */
@Composable
fun NoteCard(note: NoteUi, modifier: Modifier = Modifier) {
    val colors = Forma.colors
    val icon = when (note.kind) {
        NoteKind.CONSTRAINT, NoteKind.OMITTED -> FormaIcons.Info
        NoteKind.SHORTENED -> FormaIcons.Clock
        NoteKind.RETURN -> FormaIcons.Leaf
        NoteKind.EASIER -> FormaIcons.Easier
        NoteKind.QUIET -> FormaIcons.Quiet
        NoteKind.PRO_LIMIT -> FormaIcons.Sparkle
        NoteKind.REPLACED -> FormaIcons.Swap
        else -> FormaIcons.Info
    }
    Row(
        modifier.fillMaxWidth().clip(Shapes.small).background(colors.surfaceSecondary).border(1.dp, colors.border, Shapes.small).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(note.text, style = Forma.type.supporting, color = colors.textSecondary)
    }
}

/** Icon conveying the direction of a change alongside its text. */
fun changeIcon(tone: ChangeTone?): ImageVector = when (tone) {
    ChangeTone.UP -> FormaIcons.ArrowUp
    ChangeTone.DOWN -> FormaIcons.ArrowDown
    ChangeTone.SAME -> FormaIcons.Equals
    ChangeTone.SWAP -> FormaIcons.Swap
    ChangeTone.INFO, null -> FormaIcons.Info
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    FormaCard(modifier, padding = 16.dp, fillWidth = false) {
        Text(value, style = Forma.type.numberMedium, color = Forma.colors.text)
        Spacer(Modifier.size(4.dp))
        Text(label, style = Forma.type.caption, color = Forma.colors.textSecondary)
    }
}
