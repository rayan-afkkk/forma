package app.forma.harness

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import app.forma.ui.theme.FormaFonts

/** The same bundled font files the Android app uses (app/src/main/res/font), loaded as JVM resources. */
object HarnessFonts {
    val fonts: FormaFonts by lazy {
        FormaFonts(
            display = FontFamily(
                Font("newsreader_display_regular.ttf", FontWeight.Normal),
                Font("newsreader_display_medium.ttf", FontWeight.Medium),
            ),
            serifText = FontFamily(Font("newsreader_text_medium.ttf", FontWeight.Medium)),
            sans = FontFamily(
                Font("inter_regular.ttf", FontWeight.Normal),
                Font("inter_medium.ttf", FontWeight.Medium),
                Font("inter_semibold.ttf", FontWeight.SemiBold),
            ),
            numbers = FontFamily(Font("inter_display_semibold.ttf", FontWeight.SemiBold)),
        )
    }
}
