package app.forma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Design tokens. Colours were checked for WCAG contrast: body and secondary text are at least
 * 4.5:1 on every surface they appear on in both themes (see docs/DESIGN.md for the table).
 */

/** A restrained pastel family: container, readable content, secondary content (≥4.5:1) and a definition border. */
@Immutable
data class Pastel(val container: Color, val content: Color, val secondary: Color, val border: Color)

@Immutable
data class Pastels(
    val blue: Pastel,
    val mint: Pastel,
    val green: Pastel,
    val peach: Pastel,
    val lavender: Pastel,
    val butter: Pastel,
    val rose: Pastel,
)

@Immutable
data class FormaColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Orange accent for fills and icons. */
    val accent: Color,
    /** Orange accent tuned for text on this theme's surfaces. */
    val accentText: Color,
    val danger: Color,
    val scrim: Color,
    val pastels: Pastels,
    /** Light theme gives pastel cards a border because pastel-on-ivory contrast is low. */
    val pastelBorders: Boolean,
)

private val SharedPastels = Pastels(
    blue = Pastel(Color(0xFFA8BDD6), Color(0xFF13202E), Color(0xFF313F50), Color(0xFF8DA1B8)),
    mint = Pastel(Color(0xFFAFD8C6), Color(0xFF10291F), Color(0xFF304C40), Color(0xFF92B8A8)),
    green = Pastel(Color(0xFFC3D7A2), Color(0xFF1F2B12), Color(0xFF404D2F), Color(0xFFA5B888)),
    peach = Pastel(Color(0xFFF0C3A4), Color(0xFF3A2212), Color(0xFF5E422F), Color(0xFFCFA68A)),
    lavender = Pastel(Color(0xFFC8BDE3), Color(0xFF261E3D), Color(0xFF463E5E), Color(0xFFABA0C5)),
    butter = Pastel(Color(0xFFE8D695), Color(0xFF332A0C), Color(0xFF574C27), Color(0xFFC7B77C)),
    rose = Pastel(Color(0xFFE6B5BC), Color(0xFF3B1C22), Color(0xFF5D3B41), Color(0xFFC799A0)),
)

val DarkColors = FormaColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF211F1A),
    surfaceSecondary = Color(0xFF191814),
    border = Color(0xFF343129),
    borderStrong = Color(0xFF5A564C),
    text = Color(0xFFF4EEE4),
    textSecondary = Color(0xFFB6B0A6),
    textMuted = Color(0xFF918B82),
    primary = Color(0xFFF4EEE4),
    onPrimary = Color(0xFF191814),
    accent = Color(0xFFFF713D),
    accentText = Color(0xFFFF713D),
    danger = Color(0xFFFF7275),
    scrim = Color(0xB3000000),
    pastels = SharedPastels,
    pastelBorders = false,
)

val LightColors = FormaColors(
    isDark = false,
    background = Color(0xFFF6F2EA),
    surface = Color(0xFFFFFCF6),
    surfaceSecondary = Color(0xFFEEE8DD),
    border = Color(0xFFD9D0C1),
    borderStrong = Color(0xFF8C8478),
    text = Color(0xFF1C1A16),
    textSecondary = Color(0xFF5A544B),
    textMuted = Color(0xFF6B6358),
    primary = Color(0xFF1C1A16),
    onPrimary = Color(0xFFF6F2EA),
    accent = Color(0xFFFF713D),
    accentText = Color(0xFFB4441A),
    danger = Color(0xFFB3261E),
    scrim = Color(0x80000000),
    pastels = SharedPastels,
    pastelBorders = true,
)

/** Bundled font families, supplied by the platform (Android font resources or desktop files). */
@Immutable
data class FormaFonts(
    /** Newsreader at display optical size, for page titles. */
    val display: FontFamily,
    /** Newsreader at text optical size, for card titles. */
    val serifText: FontFamily,
    /** Inter, for interface text. */
    val sans: FontFamily,
    /** Inter display cut, for large numbers. */
    val numbers: FontFamily,
)

@Immutable
data class FormaType(
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val cardTitleSmall: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val supporting: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val eyebrow: TextStyle,
    /** Exercise name in the player. Sans serif for readability while moving. */
    val exerciseName: TextStyle,
    val numberHuge: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
)

fun formaType(fonts: FormaFonts): FormaType {
    val tabular = "tnum"
    return FormaType(
        pageTitle = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em),
        sectionTitle = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 33.sp),
        cardTitle = TextStyle(fontFamily = fonts.serifText, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
        cardTitleSmall = TextStyle(fontFamily = fonts.serifText, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp),
        body = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyStrong = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        supporting = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        caption = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        label = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        eyebrow = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
        exerciseName = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
        numberHuge = TextStyle(fontFamily = fonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 76.sp, lineHeight = 84.sp, fontFeatureSettings = tabular),
        numberLarge = TextStyle(fontFamily = fonts.numbers, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, fontFeatureSettings = tabular),
        numberMedium = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = tabular),
    )
}

val LocalFormaColors = staticCompositionLocalOf { DarkColors }
val LocalFormaType = staticCompositionLocalOf<FormaType> { error("FormaTheme is not set") }

object Forma {
    val colors: FormaColors
        @Composable get() = LocalFormaColors.current
    val type: FormaType
        @Composable get() = LocalFormaType.current
}

@Composable
fun FormaTheme(darkTheme: Boolean, fonts: FormaFonts, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val type = remember(fonts) { formaType(fonts) }
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceSecondary, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = colors.danger, scrim = colors.scrim,
            secondary = colors.accent, tertiary = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceSecondary, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = colors.danger, scrim = colors.scrim,
            secondary = colors.accentText, tertiary = colors.accentText,
        )
    }
    val m3Type = MaterialTheme.typography.copy(
        bodyLarge = type.body, bodyMedium = type.supporting, bodySmall = type.caption,
        labelLarge = type.label, labelMedium = type.labelSmall, labelSmall = type.labelSmall,
        titleLarge = type.cardTitle, titleMedium = type.bodyStrong, titleSmall = type.supporting,
        headlineSmall = type.sectionTitle, headlineMedium = type.sectionTitle, headlineLarge = type.pageTitle,
    )
    CompositionLocalProvider(LocalFormaColors provides colors, LocalFormaType provides type) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}
