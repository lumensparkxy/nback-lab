package com.example.nback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nback.engine.StimulusType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ColourRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val mask = mutableStateOf(7)
    private val values = mutableStateOf<Map<StimulusType, Int>>(emptyMap())
    private val gallery = mutableStateOf(false)
    private val expected = listOf(0xFFD32F2F, 0xFF1565C0, 0xFF2E7D32, 0xFFFDD835, 0xFF7B1FA2, 0xFFEF6C00)

    @Before fun show() {
        compose.setContent {
            MaterialTheme {
                if (gallery.value) Column(Modifier.background(Color.White)) {
                    repeat(3) { row -> Row {
                        repeat(2) { column ->
                            val colour = row * 2 + column
                            StimulusView(mapOf(StimulusType.COLOUR to colour, StimulusType.NUMBER to colour),
                                6, Modifier.size(150.dp), "palette_$colour")
                        }
                    } }
                } else StimulusView(values.value, mask.value, Modifier.size(300.dp).background(Color.White))
            }
        }
    }

    @Test fun paletteKeepsStableIdentitiesAndReadableDigits() {
        assertEquals(expected.map { it.toInt() }, StimulusColours.palette.map { it.toArgb() })
        for (fill in StimulusColours.palette) {
            val foreground = StimulusColours.foreground(fill)
            val light = maxOf(fill.luminance(), foreground.luminance())
            val dark = minOf(fill.luminance(), foreground.luminance())
            assertTrue("Digit contrast on ${fill.toArgb()}", (light + .05f) / (dark + .05f) >= 4.5f)
        }
        assertTrue("Outline identifies light tiles against inactive cells", (StimulusColours.inactive.luminance() + .05f) / .05f >= 3f)
        compose.runOnIdle { gallery.value = true }
        compose.waitForIdle()
        capture("colour-palette")
    }

    @Test fun allModesRenderOnlySelectedAttributesAndHideTheWholeStimulus() {
        for (mode in 1..7) for (colour in 0..5) {
            compose.runOnIdle {
                mask.value = mode
                values.value = mapOf(StimulusType.POSITION to 4, StimulusType.COLOUR to colour, StimulusType.NUMBER to 7)
            }
            val node = compose.onNodeWithTag("grid")
            node.assertContentDescriptionEquals(if (mode and 1 != 0) "Visual position grid" else "Visual stimulus")
            val before = node.captureToImage().toPixelMap()
            val x = (before.width * .395f).toInt()
            val y = before.height / 2
            assertEquals("Visible fill: mode=$mode colour=$colour",
                if (mode and 2 != 0) expected[colour].toInt() else 0xFF18675A.toInt(), before[x, y].toArgb())
            if (mode and 2 != 0) assertEquals("The tile has a dark boundary", Color.Black.toArgb(),
                before[(before.width * .345f).toInt(), y].toArgb())
            compose.runOnIdle { values.value = values.value + (StimulusType.NUMBER to 0) }
            val after = node.captureToImage().toPixelMap()
            var changed = 0
            var changedForeground = 0
            val foreground = StimulusColours.foreground(if (mode and 2 != 0) StimulusColours.palette[colour] else StimulusColours.neutral).toArgb()
            for (px in 0 until before.width) for (py in 0 until before.height) {
                if (before[px, py].toArgb() != after[px, py].toArgb()) {
                    changed++
                    if (before[px, py].toArgb() == foreground) changedForeground++
                }
            }
            assertEquals("Number must vary only when active: mode=$mode", mode and 4 != 0, changed > 0)
            if (mode and 4 != 0) assertTrue("Digit pixels use the contrasting foreground", changedForeground > 0)
            compose.runOnIdle { values.value = emptyMap() }
            val blank = node.captureToImage().toPixelMap()
            assertEquals("No colour or digit remains in a blank frame", 0xFFE1E6DF.toInt(), blank[blank.width / 2, blank.height / 2].toArgb())
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "f004-colour-qa").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
