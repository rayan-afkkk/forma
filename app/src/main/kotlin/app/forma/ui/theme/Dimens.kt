package app.forma.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Dimens {
    val cardRadius = 26.dp
    val sheetRadius = 28.dp
    val tileRadius = 24.dp
    val cardGap = 12.dp
    val sectionGap = 28.dp
    val cardPadding = 20.dp
    val touchTarget = 48.dp
    val primaryButtonHeight = 56.dp
    val secondaryButtonHeight = 52.dp
    val bottomBarHeight = 64.dp
}

object Shapes {
    val card = RoundedCornerShape(Dimens.cardRadius)
    val tile = RoundedCornerShape(Dimens.tileRadius)
    val sheet = RoundedCornerShape(topStart = Dimens.sheetRadius, topEnd = Dimens.sheetRadius)
    val pill = RoundedCornerShape(percent = 50)
    val small = RoundedCornerShape(14.dp)
}

/** Horizontal screen padding: 20dp normally, 16dp on narrow screens or with very large text. */
val LocalScreenPadding = staticCompositionLocalOf { 20.dp }

/** True when the layout should prefer a single column (narrow screen or large text). */
val LocalCompactLayout = staticCompositionLocalOf { false }

/** True when the person asked the system to reduce or remove animations. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Layout {
    val screenPadding: Dp
        @Composable get() = LocalScreenPadding.current
    val compact: Boolean
        @Composable get() = LocalCompactLayout.current
}
