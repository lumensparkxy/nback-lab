package com.maswadkar.nback

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.maswadkar.nback.engine.MonotonicClock
import com.maswadkar.nback.engine.SessionScreen
import com.maswadkar.nback.engine.StimulusType
import com.maswadkar.nback.engine.VisualSession
import com.maswadkar.nback.engine.activeTypes
import com.maswadkar.nback.engine.generateSequence
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.random.Random

class MultiTypeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var time = 0L
    private val game = VisualSession.withTypes(MonotonicClock { time }) { type, n -> generateSequence(Random(type.bit), n, type.cardinality) }
    private val state = mutableStateOf(game.state)
    private val settings = mutableStateOf(SettingsState(loading = false))
    private val fontScale = mutableStateOf(1f)
    private fun publish() { state.value = game.state }
    private fun tag(type: StimulusType) = if (type == StimulusType.POSITION) "match" else "match_${type.bit}"
    @Before fun show() {
        compose.setContent { TestContent() }
    }
    @Composable private fun TestContent() {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                NBackTheme {
                    SessionContent(state.value,
                        { game.start(settings.value.level, modeMask = settings.value.modeMask); publish() }, {}, { game.home(); publish() }, settings.value,
                        onToggleType = { type ->
                            val mask = settings.value.modeMask xor type.bit
                            if (mask != 0) settings.value = settings.value.copy(modeMask = mask)
                        }, onTypeMatch = { game.match(it); publish() })
                }
            }
    }
    @Test fun independentTogglesPreventEmptySelectionAndOnlyActiveButtonsAppear() {
        compose.onNodeWithTag("settings").performScrollTo().performClick()
        compose.onNodeWithTag("type_1").assertIsNotEnabled()
        compose.onNodeWithTag("type_2").performScrollTo().performClick()
        compose.onNodeWithTag("type_1").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("type_2").assertIsNotEnabled()
        compose.onNodeWithTag("type_4").performScrollTo().performClick()
        compose.onNodeWithTag("settings_back").performScrollTo().performClick()
        compose.onNodeWithTag("start").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(6, game.state.config.modeMask); time = 6000; game.advance(); publish() }
        compose.onNodeWithTag("match").assertDoesNotExist()
        compose.onNodeWithTag("match_2").assertIsEnabled().performClick()
        compose.onNodeWithTag("match_2").assertIsNotEnabled()
        compose.onNodeWithTag("match_4").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(setOf(StimulusType.COLOUR, StimulusType.NUMBER), game.state.recordedTypes) }
    }
    @Test fun allModesKeepControlsAndGridStableAtNormalAndDoubleFont() {
        try {
            for ((orientation, config) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
                compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == config }
                // ComponentActivity has no app onCreate renderer. Reattach this fixture to
                // the recreated activity; preserve the externally owned fake clock/state.
                compose.activityRule.scenario.onActivity { it.setContent { TestContent() } }
                for (scale in listOf(1f, 2f)) for (mask in 1..7) for (practice in listOf(false, true)) {
                    compose.runOnIdle {
                        fontScale.value = scale; game.home(); time = 0; game.start(modeMask = mask, practice = practice); publish()
                    }
                    val grid = compose.onNodeWithTag("grid")
                    val warmup = grid.fetchSemanticsNode().boundsInRoot
                    if (warmup.width <= 0 || warmup.height <= 0) {
                        screenshot("failed-mode-$mask-font-${scale.toInt()}-orientation-$config")
                        fail("Nonempty stimulus area mask=$mask font=$scale orientation=$config\n" +
                            compose.onRoot(useUnmergedTree = true).printToString())
                    }
                    if (warmup.height < with(compose.density) { 96.dp.toPx() }) screenshot("failed-small-grid-$mask-font-$scale-orientation-$config-practice-$practice")
                    grid.assertHeightIsAtLeast(96.dp).assertWidthIsAtLeast(96.dp)
                    activeTypes(mask).forEach { compose.onNodeWithTag(tag(it)).assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp) }
                    val buttons = activeTypes(mask).map { compose.onNodeWithTag(tag(it)).fetchSemanticsNode().boundsInRoot }
                    val row = compose.onNodeWithTag("response_row").fetchSemanticsNode().boundsInRoot
                    val area = compose.onNodeWithTag("playing_area").fetchSemanticsNode().boundsInRoot
                    buttons.forEach { bounds ->
                        assertTrue("Full response button within content", bounds.top >= area.top && bounds.bottom <= area.bottom + 1f)
                        assertEquals("Responses share one row", buttons.first().top, bounds.top, 1f)
                        assertEquals("Responses share equal heights", buttons.first().height, bounds.height, 1f)
                    }
                    buttons.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.left) }
                    assertEquals("Response row centered under stimulus", compose.onNodeWithTag("playing_area").fetchSemanticsNode().boundsInRoot.center.x, row.center.x, 1f)
                    if (scale == 1f) assertTrue("Normal response row stays compact", row.height <= with(compose.density) { 112.dp.toPx() })
                    compose.runOnIdle { time = 6000; game.advance(); publish() }
                    assertEquals(warmup, grid.fetchSemanticsNode().boundsInRoot)
                    activeTypes(mask).forEach { type ->
                        compose.onNodeWithTag(tag(type)).assertIsEnabled().performClick()
                        val recordedBounds = compose.onNodeWithTag(tag(type)).fetchSemanticsNode().boundsInRoot
                        assertTrue("Recorded button stays fully visible", recordedBounds.bottom <= area.bottom + 1f)
                        val response = compose.onNodeWithTag(tag(type)).fetchSemanticsNode().config
                        if (response.contains(SemanticsProperties.StateDescription)) {
                            assertEquals("✓ Recorded", response[SemanticsProperties.StateDescription])
                        }
                        assertEquals("Recorded status must not shift grid mask=$mask font=$scale", warmup, grid.fetchSemanticsNode().boundsInRoot)
                    }
                    screenshot("mode-$mask-font-${scale.toInt()}-orientation-$config${if (practice) "-practice" else ""}")
                }
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun resultsExposeEachTypeWithoutFabricatingInactiveScores() {
        compose.runOnIdle { game.start(modeMask = 6); time = 66000; game.advance(); publish() }
        assertEquals(SessionScreen.RESULTS, game.state.screen)
        compose.onNodeWithTag("accuracy").assertDoesNotExist()
        compose.onNodeWithTag("accuracy_2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("accuracy_4").performScrollTo().assertIsDisplayed()
    }
    @Test fun compactControlsAlwaysExposeFullNamesAndIndependentRecordedStates() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { Box(Modifier.heightIn(max = 480.dp)) { TestContent() } }
        }
        compose.runOnIdle { fontScale.value = 2f; game.start(modeMask = 7); publish() }
        val names = mapOf(StimulusType.POSITION to "Position", StimulusType.COLOUR to "Colour", StimulusType.NUMBER to "Number")
        for ((type, name) in names) {
            compose.onNodeWithTag(tag(type)).assertContentDescriptionEquals("$name match")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Watch the warm-up."))
        }
        compose.runOnIdle { time = 6000; game.advance(); publish() }
        for (type in names.keys) {
            compose.onNodeWithTag(tag(type)).assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Tap if this type matches."))
                .performClick()
            compose.onNodeWithTag(tag(type)).assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "✓ Recorded"))
        }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "f004-qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
