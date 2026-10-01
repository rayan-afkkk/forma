package app.forma.harness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forma.core.engine.DayState
import app.forma.core.model.MovementPattern
import app.forma.core.model.ThemeMode
import app.forma.presentation.ChangeTone
import app.forma.presentation.ItemUi
import app.forma.presentation.Tab
import app.forma.presentation.today.DayUi
import app.forma.presentation.today.WeekUi
import app.forma.ui.components.CategoryTile
import app.forma.ui.components.DemonstrationArea
import app.forma.ui.components.ExerciseRow
import app.forma.ui.components.FilterChip
import app.forma.ui.components.FormaBottomBar
import app.forma.ui.components.FormaCard
import app.forma.ui.components.PageHeader
import app.forma.ui.components.PastelCard
import app.forma.ui.components.PillSegmentedControl
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.SecondaryButton
import app.forma.ui.components.SettingsGroup
import app.forma.ui.components.SettingsRow
import app.forma.ui.components.Divider
import app.forma.ui.components.WeekStrip
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.FormaIllustration
import app.forma.ui.illustrations.Illustration
import app.forma.ui.theme.Forma
import org.junit.Test
import java.io.File

class GalleryTest {
    private val out = File(System.getProperty("forma.screenshotDir") ?: "build/screenshots", "dev")

    @Composable
    private fun Gallery() {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PageHeader("Today", eyebrow = "Good morning, Sam")
            WeekStrip(
                WeekUi(
                    listOf(DayState.DONE, DayState.NOT_DONE, DayState.PARTIAL, DayState.PLANNED, DayState.REST, DayState.PLANNED, DayState.REST)
                        .mapIndexed { i, s -> DayUi("MTWTFSS"[i].toString(), 28 + i, s, i == 3, s != DayState.REST, "") },
                    "1 of 3 planned sessions done this week",
                ),
            )
            PastelCard(Forma.colors.pastels.blue) {
                Text("Full body A", style = Forma.type.cardTitle, color = Forma.colors.pastels.blue.content)
                Text("About 28 min · Dumbbells", style = Forma.type.supporting, color = Forma.colors.pastels.blue.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile("Beginner", "6 sessions", FormaIcons.Leaf, Forma.colors.pastels.green, false, {}, Modifier.weight(1f))
                CategoryTile("Mobility", "4 sessions", FormaIcons.Leaf, Forma.colors.pastels.mint, true, {}, Modifier.weight(1f))
            }
            PillSegmentedControl(ThemeMode.entries, ThemeMode.SYSTEM, { it.label }, {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Favorites", true, {})
                FilterChip("Up to 15 min", false, {})
            }
            PrimaryButton("Start workout", {}, icon = FormaIcons.Play)
            SecondaryButton("Adjust today", {}, icon = FormaIcons.Sliders)
            FormaCard {
                ExerciseRow(ItemUi("squat", "goblet_squat", "Goblet squat", "3 × 8–12 reps · 7.5 kg", "75 sec rest", MovementPattern.SQUAT, false, emptyList(), "", false, "You reached the top of your target with 5 kg last time, so today uses 7.5 kg.", ChangeTone.UP), {})
            }
            SettingsGroup(title = "Preferences") {
                SettingsRow("Equipment", {}, icon = FormaIcons.Dumbbell, description = "Home · Dumbbells 2.5–20 kg")
                Divider()
                SettingsRow("Delete local data", {}, icon = FormaIcons.Trash, destructive = true)
            }
            DemonstrationArea(MovementPattern.SQUAT, "Goblet squat", compact = true)
            Row { FormaIllustration(Illustration.SPROUT, width = 120.dp); FormaIllustration(Illustration.REST, width = 120.dp, accent = Forma.colors.pastels.lavender.container) }
            Spacer(Modifier.height(8.dp))
            FormaBottomBar(Tab.TODAY, {})
        }
    }

    @Test
    fun renderGallery() {
        snapshot(File(out, "gallery-dark.png"), Device("tall", 411, 1700, 2f)) { Gallery() }
        snapshot(File(out, "gallery-light.png"), Device("tall", 411, 1700, 2f), dark = false) { Gallery() }
    }
}
