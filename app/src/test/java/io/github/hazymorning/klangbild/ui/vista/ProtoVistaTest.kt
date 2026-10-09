package io.github.hazymorning.klangbild.ui.vista

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hazymorning.klangbild.ui.Fixtures
import io.github.hazymorning.klangbild.vista.ProtoScene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The performance prototype renders, and how long its layers take to bake on the JVM. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ProtoVistaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renders() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Fixtures.Still { ProtoVista(timelapse = false) } }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/screenshots/vista/proto.png")
    }

    @Test
    fun bakeTimes() {
        ProtoScene.bake(960, 540) // warm up
        for ((w, h) in listOf(1920 to 1080, 3840 to 2160)) {
            val b = ProtoScene.bake(w, h)
            println("proto bake ${w}x$h: ${b.bakeMs} ms")
        }
    }
}
