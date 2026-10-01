package app.forma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Shapes

/**
 * Wide pill-shaped segmented control. The selected segment is filled and announced as selected;
 * labels may wrap to two lines with large text instead of being clipped.
 */
@Composable
fun <T> PillSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Forma.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.pill)
            .background(colors.surface)
            .border(1.dp, colors.border, Shapes.pill)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(Shapes.pill)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = Forma.type.label.copy(fontSize = Forma.type.supporting.fontSize),
                    color = if (isSelected) colors.onPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Pill filter chip. Selected chips are filled and show a check mark. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = Forma.colors
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(Shapes.pill)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.border, Shapes.pill)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) colors.onPrimary else colors.text
        if (selected) {
            Icon(FormaIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = Forma.type.label.copy(fontSize = Forma.type.supporting.fontSize), color = tint)
    }
}

/** A row with a switch. The whole row is the touch target and is announced as a switch. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Forma.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Forma.type.bodyStrong, color = if (enabled) colors.text else colors.textMuted)
            if (description != null) Text(description, style = Forma.type.supporting, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceSecondary,
                uncheckedBorderColor = colors.borderStrong,
            ),
        )
    }
}

/** A large option card used in onboarding and settings (single choice). */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
) {
    val colors = Forma.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.text else colors.border, Shapes.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Forma.type.bodyStrong, color = colors.text)
            if (description != null) Text(description, style = Forma.type.supporting, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (selected) colors.text else Color.Transparent)
                .border(1.5.dp, if (selected) colors.text else colors.borderStrong, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(FormaIcons.Check, contentDescription = null, tint = colors.background, modifier = Modifier.size(16.dp))
        }
    }
}

/** A large minus / value / plus control for the player and set editing. */
@Composable
fun Stepper(
    value: String,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    minusDescription: String = "Decrease $label",
    plusDescription: String = "Increase $label",
    large: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        RoundIconButton(FormaIcons.Minus, minusDescription, onMinus, size = if (large) 60.dp else 48.dp, outlined = true, enabled = minusEnabled)
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = "$value $label" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = if (large) Forma.type.numberHuge else Forma.type.numberLarge, color = Forma.colors.text, textAlign = TextAlign.Center)
            Text(label, style = Forma.type.supporting, color = Forma.colors.textSecondary, textAlign = TextAlign.Center)
        }
        RoundIconButton(FormaIcons.Plus, plusDescription, onPlus, size = if (large) 60.dp else 48.dp, outlined = true, enabled = plusEnabled)
    }
}

/** Single-line text field with a visible label. */
@Composable
fun FormaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalizeWords: Boolean = false,
    suffix: String? = null,
) {
    val colors = Forma.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Forma.type.caption, color = colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Forma.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Shapes.small)
                        .background(colors.surface)
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.text else colors.border, Shapes.small)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = Forma.type.body, color = colors.textMuted)
                        }
                        inner()
                    }
                    if (suffix != null) Text(suffix, style = Forma.type.body, color = colors.textSecondary)
                }
            },
        )
    }
}

/** Simple clickable wrapper with a minimum touch size, for custom row content. */
fun Modifier.touchable(onClick: () -> Unit, label: String? = null): Modifier =
    this.heightIn(min = 48.dp).clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
