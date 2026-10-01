package app.forma.ui.screens.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.forma.core.model.ExploreCategory
import app.forma.presentation.explore.ExploreFilter
import app.forma.presentation.explore.ExploreStateHolder
import app.forma.presentation.explore.ExploreUiState
import app.forma.presentation.explore.ProgramCardUi
import app.forma.presentation.explore.WorkoutCardUi
import app.forma.ui.app.HandleEffects
import app.forma.ui.app.LocalAppServices
import app.forma.ui.components.Badge
import app.forma.ui.components.CategoryTile
import app.forma.ui.components.EmptyState
import app.forma.ui.components.FilterChip
import app.forma.ui.components.FormaBottomSheet
import app.forma.ui.components.FormaCard
import app.forma.ui.components.FormaTextField
import app.forma.ui.components.LoadingState
import app.forma.ui.components.NoteCard
import app.forma.ui.components.PageHeader
import app.forma.ui.components.PrimaryButton
import app.forma.ui.components.ProBadge
import app.forma.ui.components.RoundIconButton
import app.forma.ui.components.SectionLabel
import app.forma.ui.icons.FormaIcons
import app.forma.ui.illustrations.Illustration
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.screens.common.MetaItem
import app.forma.ui.theme.Forma
import app.forma.ui.theme.Layout
import app.forma.ui.theme.Pastel
import app.forma.ui.theme.Pastels

@Composable
fun ExploreRoute() {
    val services = LocalAppServices.current
    val holder = rememberStateHolder("explore") { ExploreStateHolder(it, services) }
    val state by holder.state.collectUiState()
    HandleEffects(holder)
    ExploreScreen(state, holder)
}

fun Pastels.forCategory(category: ExploreCategory): Pastel = when (category) {
    ExploreCategory.BEGINNER -> green
    ExploreCategory.FULL_BODY -> blue
    ExploreCategory.DUMBBELLS -> butter
    ExploreCategory.BODYWEIGHT -> rose
    ExploreCategory.SHORT -> peach
    ExploreCategory.MOBILITY -> mint
}

fun categoryIcon(category: ExploreCategory) = when (category) {
    ExploreCategory.BEGINNER -> FormaIcons.Flag
    ExploreCategory.FULL_BODY -> FormaIcons.Bodyweight
    ExploreCategory.DUMBBELLS -> FormaIcons.Dumbbell
    ExploreCategory.BODYWEIGHT -> FormaIcons.You
    ExploreCategory.SHORT -> FormaIcons.Timer
    ExploreCategory.MOBILITY -> FormaIcons.Leaf
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(state: ExploreUiState, holder: ExploreStateHolder) {
    if (state.loading) {
        LoadingState()
        return
    }
    val pad = Layout.screenPadding
    val columns = if (Layout.compact) 1 else 2
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = pad, end = pad, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader("Find your next session", subtitle = "Practical sessions that fit your equipment, time and space.")
            Spacer(Modifier.height(8.dp))
            FormaTextField(state.query, holder::setQuery, "Search", placeholder = "Search sessions or exercises")
        }
        item { SectionLabel("Categories", Modifier.padding(start = 4.dp, top = 8.dp)) }
        state.categories.chunked(columns).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        CategoryTile(
                            label = c.category.label,
                            detail = if (c.count == 1) "1 session" else "${c.count} sessions",
                            icon = categoryIcon(c.category),
                            pastel = Forma.colors.pastels.forCategory(c.category),
                            selected = state.category == c.category,
                            onClick = { holder.selectCategory(c.category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                ExploreFilter.entries.forEach { f ->
                    FilterChip(f.label, f in state.filters, { holder.toggleFilter(f) }, icon = if (f == ExploreFilter.FAVORITES) FormaIcons.Heart else null)
                }
            }
        }

        if (!state.isFiltering) {
            item { SectionLabel("Programs", Modifier.padding(start = 4.dp, top = 12.dp)) }
            items(state.programs, key = { "program-${it.id}" }) { ProgramCard(it) { holder.openProgram(it.id) } }
        }

        item {
            SectionLabel(
                if (state.isFiltering) "${state.results.size} ${if (state.results.size == 1) "session" else "sessions"}" else "Sessions",
                Modifier.padding(start = 4.dp, top = 12.dp),
            )
        }
        if (state.results.isEmpty()) {
            item {
                EmptyState(
                    Illustration.SEARCH,
                    title = "Nothing matches yet",
                    body = "Try fewer filters or a different word.",
                    actionLabel = "Clear filters",
                    onAction = holder::clearFilters,
                )
            }
        }
        items(state.results, key = { it.templateId }) { card ->
            WorkoutCard(card, onOpen = { holder.open(card.templateId) }, onFavorite = { holder.toggleFavorite(card.templateId) })
        }
    }

    state.programSheet?.let { program ->
        FormaBottomSheet(onDismissRequest = holder::closeProgram, title = program.name) {
            Text(program.summary, style = Forma.type.body, color = Forma.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(program.detail, style = Forma.type.supporting, color = Forma.colors.textMuted)
            program.unavailableReason?.let {
                Spacer(Modifier.height(12.dp))
                NoteCard(app.forma.presentation.NoteUi(app.forma.core.model.NoteKind.CONSTRAINT, it))
            }
            Spacer(Modifier.height(20.dp))
            when {
                program.enrolled -> Text("This is your current program.", style = Forma.type.bodyStrong, color = Forma.colors.text)
                program.locked -> PrimaryButton("Unlock with Pro", { holder.enroll(program.id) }, icon = FormaIcons.Sparkle)
                else -> PrimaryButton("Use this program", { holder.enroll(program.id) }, enabled = program.unavailableReason == null)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Switching programs keeps all of your history and exclusions.",
                style = Forma.type.caption, color = Forma.colors.textMuted,
            )
        }
    }
}

@Composable
private fun ProgramCard(program: ProgramCardUi, onClick: () -> Unit) {
    FormaCard(onClick = onClick, onClickLabel = "Program details") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(program.name, style = Forma.type.cardTitle, color = Forma.colors.text, modifier = Modifier.weight(1f))
            when {
                program.enrolled -> Badge("Current", icon = FormaIcons.Check)
                program.locked -> ProBadge()
                else -> Badge("Free")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(program.summary, style = Forma.type.supporting, color = Forma.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(program.detail, style = Forma.type.caption, color = Forma.colors.textMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutCard(card: WorkoutCardUi, onOpen: () -> Unit, onFavorite: () -> Unit) {
    FormaCard(onClick = onOpen, onClickLabel = "Preview session") {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                card.programName?.let { Text(it, style = Forma.type.caption, color = Forma.colors.textMuted) }
                Text(card.name, style = Forma.type.cardTitleSmall, color = Forma.colors.text)
            }
            RoundIconButton(
                if (card.favorite) FormaIcons.HeartFilled else FormaIcons.Heart,
                if (card.favorite) "Remove ${card.name} from favorites" else "Add ${card.name} to favorites",
                onFavorite,
                tint = if (card.favorite) Forma.colors.accent else Forma.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(card.description, style = Forma.type.supporting, color = Forma.colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaItem(FormaIcons.Clock, card.duration)
            MetaItem(if (card.equipment == "No equipment") FormaIcons.Bodyweight else FormaIcons.Dumbbell, card.equipment)
            MetaItem(FormaIcons.Target, card.difficulty)
        }
        if (card.locked) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProBadge()
                Spacer(Modifier.width(8.dp))
                Icon(FormaIcons.Lock, contentDescription = null, tint = Forma.colors.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Preview free, start with Pro", style = Forma.type.caption, color = Forma.colors.textMuted)
            }
        }
    }
}
