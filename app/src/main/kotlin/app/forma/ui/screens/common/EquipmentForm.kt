package app.forma.ui.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forma.core.model.WeightUnit
import app.forma.presentation.equipment.DumbbellKind
import app.forma.presentation.equipment.EquipmentDraft
import app.forma.ui.components.ChoiceCard
import app.forma.ui.components.FilterChip
import app.forma.ui.components.FormaCard
import app.forma.ui.components.FormaTextField
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PillSegmentedControl
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.components.ToggleRow
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Forma
import app.forma.presentation.NoteUi
import app.forma.core.model.NoteKind

/**
 * Equipment entry: bodyweight only or dumbbells (adjustable range or fixed weights, pair or
 * single), an optional bench, and the unit. Used in onboarding and in You › Equipment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentForm(draft: EquipmentDraft, onChange: (EquipmentDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            title = "Bodyweight only",
            description = "No equipment. Workouts use the floor and a wall.",
            selected = !draft.hasDumbbells,
            onClick = { onChange(draft.copy(hasDumbbells = false)) },
            icon = FormaIcons.Bodyweight,
        )
        ChoiceCard(
            title = "Dumbbells",
            description = "Adjustable or fixed weights.",
            selected = draft.hasDumbbells,
            onClick = { onChange(draft.copy(hasDumbbells = true)) },
            icon = FormaIcons.Dumbbell,
        )

        if (draft.hasDumbbells) {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Units")
            PillSegmentedControl(WeightUnit.entries, draft.unit, { if (it == WeightUnit.KG) "Kilograms" else "Pounds" }, { onChange(draft.withUnit(it)) })
            SectionLabel("Type")
            PillSegmentedControl(DumbbellKind.entries, draft.kind, { it.label }, { onChange(draft.copy(kind = it)) })

            when (draft.kind) {
                DumbbellKind.ADJUSTABLE -> FormaCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormaTextField(draft.adjustableMin, { onChange(draft.copy(adjustableMin = it)) }, "Lightest", Modifier.weight(1f), keyboardType = KeyboardType.Decimal, suffix = draft.unit.symbol)
                        FormaTextField(draft.adjustableMax, { onChange(draft.copy(adjustableMax = it)) }, "Heaviest", Modifier.weight(1f), keyboardType = KeyboardType.Decimal, suffix = draft.unit.symbol)
                    }
                    Spacer(Modifier.height(10.dp))
                    FormaTextField(draft.adjustableIncrement, { onChange(draft.copy(adjustableIncrement = it)) }, "Changes in steps of", keyboardType = KeyboardType.Decimal, suffix = draft.unit.symbol)
                    Spacer(Modifier.height(14.dp))
                    PillSegmentedControl(listOf(true, false), draft.adjustablePair, { if (it) "A pair" else "Just one" }, { onChange(draft.copy(adjustablePair = it)) })
                }
                DumbbellKind.FIXED -> FormaCard {
                    Text("Tap each weight you own", style = Forma.type.supporting, color = Forma.colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (draft.commonWeights + draft.fixed.keys.filter { it !in draft.commonWeights }).sorted().forEach { w ->
                            FilterChip(draft.label(w), w in draft.fixed, { onChange(draft.toggleFixed(w)) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        FormaTextField(draft.customWeight, { onChange(draft.copy(customWeight = it)) }, "Another weight", Modifier.weight(1f), keyboardType = KeyboardType.Decimal, suffix = draft.unit.symbol)
                        Spacer(Modifier.width(10.dp))
                        SecondaryButton("Add", { onChange(draft.addCustom()) }, fillWidth = false, enabled = EquipmentDraft.parse(draft.customWeight) != null)
                    }
                    if (draft.fixed.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("How many of each?", style = Forma.type.bodyStrong, color = Forma.colors.text)
                        draft.fixedList.forEach { (w, qty) ->
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(draft.label(w), style = Forma.type.body, color = Forma.colors.text, modifier = Modifier.width(84.dp))
                                PillSegmentedControl(listOf(2, 1), qty, { if (it == 2) "Pair" else "One" }, { onChange(draft.setQuantity(w, it)) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        FormaCard(padding = 16.dp) {
            ToggleRow(
                title = "I have a bench",
                description = "A flat, stable bench. Some exercises use it.",
                checked = draft.bench,
                onCheckedChange = { onChange(draft.copy(bench = it)) },
                icon = FormaIcons.Bench,
            )
        }
        val errors = draft.errors()
        if (errors.isNotEmpty()) {
            errors.forEach { NoteCard(NoteUi(NoteKind.INFO, it)) }
        } else {
            Text("Summary: ${draft.summary()}", style = Forma.type.supporting, color = Forma.colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
