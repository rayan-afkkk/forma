package app.forma.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import app.forma.ui.theme.Forma
import app.forma.ui.theme.FormaTheme
import app.forma.ui.theme.LocalCompactLayout
import app.forma.ui.theme.LocalScreenPadding
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** A phone-sized viewport for rendering. */
data class Device(val name: String, val widthDp: Int, val heightDp: Int, val density: Float, val fontScale: Float = 1f) {
    companion object {
        /** A typical 6.1–6.4" phone (≈ 411 × 891 dp). */
        val Phone = Device("phone", 411, 891, 2.625f)

        /** A small, older phone (360 × 640 dp). */
        val SmallPhone = Device("small", 360, 640, 2f)

        /** Typical phone with the system font size at 150%. */
        val LargeText = Device("largetext", 411, 891, 2.625f, fontScale = 1.5f)

        /** Small phone with 200% text: the hardest layout case. */
        val SmallHugeText = Device("small-hugetext", 360, 640, 2f, fontScale = 2f)
    }
}

/** Renders [content] headlessly with Skia and writes a PNG. Returns the file. */
fun snapshot(
    file: File,
    device: Device = Device.Phone,
    dark: Boolean = true,
    frames: Int = 3,
    content: @Composable () -> Unit,
): File {
    file.parentFile?.mkdirs()
    ImageComposeScene(
        width = (device.widthDp * device.density).toInt(),
        height = (device.heightDp * device.density).toInt(),
        density = Density(device.density, device.fontScale),
    ) {
        HarnessTheme(dark, device) { content() }
    }.use { scene ->
        var image = scene.render(0)
        // Let effects and first-frame layout settle.
        for (i in 1 until frames) image = scene.render(i * 16_000_000L)
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoding failed")
        file.writeBytes(data.bytes)
    }
    return file
}

@Composable
fun HarnessTheme(dark: Boolean, device: Device = Device.Phone, content: @Composable () -> Unit) {
    val compact = device.widthDp < 380 || device.fontScale >= 1.5f
    FormaTheme(darkTheme = dark, fonts = HarnessFonts.fonts) {
        CompositionLocalProvider(
            LocalScreenPadding provides if (device.widthDp < 380 || device.fontScale >= 1.75f) 16.dp else 20.dp,
            LocalCompactLayout provides compact,
        ) {
            Box(Modifier.fillMaxSize().background(Forma.colors.background)) { content() }
        }
    }
}
