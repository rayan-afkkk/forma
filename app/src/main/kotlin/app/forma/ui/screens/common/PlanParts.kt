package app.forma.ui.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forma.presentation.PlanUi
import app.forma.presentation.SectionUi
import app.forma.ui.components.Divider
import app.forma.ui.components.ExerciseRow
import app.forma.ui.components.FormaCard
import app.forma.ui.components.SectionLabel
import app.forma.ui.icons.FormaIcons
import app.forma.ui.theme.Forma

/** Icon and short text, e.g. "About 25 min". */
@Composable
fun MetaItem(icon: ImageVector, text: String, color: Color = Forma.colors.textSecondary, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Forma.type.supporting, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanMeta(plan: PlanUi, color: Color = Forma.colors.textSecondary, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetaItem(FormaIcons.Clock, plan.durationText, color)
        MetaItem(if (plan.equipmentText == "No equipment") FormaIcons.Bodyweight else FormaIcons.Dumbbell, plan.equipmentText, color)
        MetaItem(FormaIcons.Overview, plan.countText, color)
    }
}

/** Warm-up / main work / cool-down sections with exercise rows. */
@Composable
fun PlanSections(sections: List<SectionUi>, onItem: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier) {
        var index = 0
        sections.forEach { section ->
            SectionLabel(section.title, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
            FormaCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    section.items.forEachIndexed { i, item ->
                        if (i > 0) Divider(startIndent = 54.dp)
                        index += 1
                        ExerciseRow(item, onClick = onItem?.let { { it(item.key) } }, index = index)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
