package app.forma.harness

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import app.forma.ui.Placeholder
import org.junit.Test

class SmokeTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun renders() = runComposeUiTest { setContent { Placeholder() } }
}
