package com.maswadkar.nback

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.maswadkar.nback.engine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class IntervalUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun sliderAndStepControlsCoverBoundariesAndUpdateExposureAndDuration() {
        val settings = mutableStateOf(SettingsState(loading = false, modeMask = 7))
        compose.setContent { NBackTheme { SessionContent(SessionState(), {}, {}, {}, settings.value,
            onInterval = { settings.value = settings.value.copy(intervalSeconds = it) }) } }
        for ((interval, exposure) in listOf(1 to 1, 7 to 1, 8 to 2, 15 to 2, 16 to 3, 30 to 3)) {
            compose.onNodeWithTag("interval").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(interval.toFloat()) }
            compose.runOnIdle { assertEquals(interval, settings.value.intervalSeconds) }
            compose.onNodeWithText("Time per turn · $interval s").assertExists()
            compose.onNodeWithText("Visible for $exposure s · Respond until the next turn").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("2 warm-up · 20 scored turns · ${22 * interval} seconds").assertExists()
        }
        compose.onNodeWithTag("interval_more").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("interval_less").performClick()
        compose.runOnIdle { assertEquals(29, settings.value.intervalSeconds) }
        compose.onNodeWithTag("interval").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("interval_less").assertIsNotEnabled()
        capture("home-interval")
    }
    @Test fun chartHasIndependentSeriesExactTableAndReadableLargeText() {
        val font = mutableStateOf(1f)
        val mask = mutableStateOf(7)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font.value)) {
                NBackTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    AccuracyChart(SessionConfig(2, modeMask = mask.value, intervalSeconds = 8), timelineRecord(mask = mask.value).outcomes())
                } }
            }
        }
        for (mode in 1..7) {
            compose.runOnIdle { mask.value = mode }
            for (type in StimulusType.entries) {
                if (mode and type.bit != 0) compose.onNodeWithTag("chart_type_${type.bit}").performScrollTo().assertIsOn()
                else compose.onNodeWithTag("chart_type_${type.bit}").assertDoesNotExist()
            }
        }
        compose.onNodeWithTag("chart_type_2").performClick().assertIsOff()
        compose.onNodeWithTag("chart_type_1").assertIsOn()
        compose.onNodeWithTag("chart_type_2").performClick()
        capture("chart")
        compose.onNodeWithTag("timeline_table").performScrollTo().performClick()
        compose.onNodeWithTag("timeline_turn_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn 1 · 0:24", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("timeline_turn_20").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn 20 · 2:56", useUnmergedTree = true).assertExists()
        compose.runOnIdle { font.value = 2f }
        compose.onNodeWithTag("timeline_turn_20").performScrollTo().assertIsDisplayed()
        capture("chart-table-font2")
        compose.onNodeWithTag("timeline_table").performScrollTo().performClick()
        compose.onNodeWithTag("timeline_turn_1").assertDoesNotExist()
        compose.onNodeWithTag("chart_type_4").performScrollTo().assertHeightIsAtLeast(48.dp)
        capture("chart-font2")
    }
    @Test fun oldResultsExplicitlyShowNoInventedTimeline() {
        compose.setContent { NBackTheme { AccuracyChart(SessionConfig(), emptyMap()) } }
        compose.onNodeWithText("Timeline unavailable for this older session.").assertIsDisplayed()
        compose.onNodeWithTag("accuracy_timeline").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "f006-qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
    }
}
