package app.forma.android.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.forma.R
import app.forma.ui.theme.FormaFonts

/** Bundled fonts (res/font): Newsreader and Inter, both SIL Open Font License 1.1. Works offline. */
val AndroidFonts = FormaFonts(
    display = FontFamily(
        Font(R.font.newsreader_display_regular, FontWeight.Normal),
        Font(R.font.newsreader_display_medium, FontWeight.Medium),
    ),
    serifText = FontFamily(Font(R.font.newsreader_text_medium, FontWeight.Medium)),
    sans = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    ),
    numbers = FontFamily(Font(R.font.inter_display_semibold, FontWeight.SemiBold)),
)
