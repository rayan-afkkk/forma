package app.forma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.forma.ui.theme.Dimens
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Shapes

/** Large cream (dark theme) or charcoal (light theme) pill. Grows with text size instead of clipping. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    val colors = Forma.colors
    val active = enabled && !busy
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.primaryButtonHeight)
            .clip(Shapes.pill)
            .background(if (active) colors.primary else colors.primary.copy(alpha = 0.38f))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (busy) "Please wait…" else text,
                style = Forma.type.label,
                color = colors.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Outlined pill for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = Forma.colors.text,
    fillWidth: Boolean = true,
) {
    val colors = Forma.colors
    Box(
        modifier
            .let { if (fillWidth) it.fillMaxWidth() else it }
            .heightIn(min = Dimens.secondaryButtonHeight)
            .clip(Shapes.pill)
            .border(1.dp, if (enabled) colors.borderStrong else colors.border, Shapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) contentColor else colors.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = Forma.type.label, color = if (enabled) contentColor else colors.textMuted, textAlign = TextAlign.Center)
        }
    }
}

/** Text-only action with a full 48dp touch target. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Forma.colors.text,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .sizeIn(minHeight = Dimens.touchTarget, minWidth = Dimens.touchTarget)
            .clip(Shapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Forma.type.label, color = if (enabled) color else Forma.colors.textMuted)
    }
}

/** Round icon button. [description] is read by screen readers. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.touchTarget,
    filled: Boolean = false,
    outlined: Boolean = false,
    enabled: Boolean = true,
    tint: Color = if (filled) Forma.colors.onPrimary else Forma.colors.text,
) {
    val colors = Forma.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .let { if (filled) it.background(if (enabled) colors.primary else colors.primary.copy(alpha = 0.38f)) else it }
            .let { if (outlined) it.border(1.dp, colors.border, CircleShape) else it }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else colors.textMuted, modifier = Modifier.size(if (size >= 56.dp) 26.dp else 22.dp))
    }
}
