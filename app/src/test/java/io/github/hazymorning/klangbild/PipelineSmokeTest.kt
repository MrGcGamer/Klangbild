package io.github.hazymorning.klangbild

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class PipelineSmokeTest {
    @Test
    fun rendersAt1080p() {
        captureRoboImage("build/screenshots/smoke_1080p.png") {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(Color(0xFF6B3A1F), Color.Black)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicText("Klangbild – 夜に駆ける – أنت عمري – 🌙")
            }
        }
    }
}
