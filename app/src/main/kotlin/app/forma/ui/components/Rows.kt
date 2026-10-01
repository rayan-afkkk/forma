package app.forma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.forma.core.model.MovementPattern
import app.forma.presentation.ItemUi
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Shapes

@Composable
fun Divider(modifier: Modifier = Modifier, startIndent: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(modifier.padding(start = startIndent).fillMaxWidth().height(1.dp).background(Forma.colors.border))
}

/** A group of settings rows inside one rounded card, with thin dividers between rows. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.card)
                .background(Forma.colors.surface)
                .border(1.dp, Forma.colors.border, Shapes.card)
                .padding(horizontal = 18.dp),
            content = content,
        )
    }
}

/** Icon, label, optional description or value, and a chevron. Destructive rows use the danger colour and say so in text. */
@Composable
fun SettingsRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Forma.colors
    val titleColor = if (destructive) colors.danger else colors.text
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (destructive) colors.danger else colors.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Forma.type.bodyStrong, color = titleColor)
            if (description != null) Text(description, style = Forma.type.supporting, color = colors.textSecondary)
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, style = Forma.type.supporting, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.7f, fill = false))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(FormaIcons.ChevronRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Forma.type.eyebrow,
        color = Forma.colors.textMuted,
        modifier = modifier.semantics { heading() },
    )
}

fun patternIcon(pattern: MovementPattern): ImageVector = when (pattern) {
    MovementPattern.WARMUP, MovementPattern.CONDITIONING -> FormaIcons.Timer
    MovementPattern.MOBILITY, MovementPattern.STRETCH -> FormaIcons.Leaf
    MovementPattern.CORE -> FormaIcons.Target
    else -> FormaIcons.Bodyweight
}

/** One exercise in a workout list: name, sets × target, load, and any change with an arrow and text. */
@Composable
fun ExerciseRow(
    item: ItemUi,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    index: Int? = null,
) {
    val colors = Forma.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (onClick != null) it.clickable(onClickLabel = "Exercise options", role = Role.Button, onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceSecondary).border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (index != null) {
                Text(index.toString(), style = Forma.type.label, color = colors.textSecondary)
            } else {
                Icon(patternIcon(item.pattern), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = Forma.type.bodyStrong, color = colors.text)
            Text(item.detail, style = Forma.type.supporting, color = colors.textSecondary)
            if (item.replacedFrom != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(FormaIcons.Swap, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Replacing ${item.replacedFrom} today", style = Forma.type.caption, color = colors.textMuted)
                }
            } else if (item.change != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(changeIcon(item.changeTone), contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.change!!, style = Forma.type.caption, color = colors.textSecondary)
                }
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(FormaIcons.ChevronRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        }
    }
}
