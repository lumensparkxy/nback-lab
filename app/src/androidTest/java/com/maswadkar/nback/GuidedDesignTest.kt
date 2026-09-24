package com.maswadkar.nback

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
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
import kotlin.random.Random

class GuidedDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scale = mutableStateOf(1f)
    private val width = mutableStateOf(390)
    private val height = mutableStateOf(844)
    private val settings = mutableStateOf(SettingsState(loading = false, modeMask = 7))
    private var time = 0L
    private val game = VisualSession.withTypes(MonotonicClock { time }) { type, n -> generateSequence(Random(type.bit), n, type.cardinality) }
    private val state = mutableStateOf(game.state)
    private fun show() {
        compose.setContent { Fixture() }
    }
    @Composable private fun Fixture() {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Preserve the specified logical viewport even on CI's smaller physical display.
            val fit = minOf(1f, maxWidth.value / width.value, maxHeight.value / height.value)
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density * fit, scale.value)) {
                NBackTheme {
                    Box(Modifier.requiredSize(width.value.dp, height.value.dp).testTag("design_viewport")) {
                        SessionContent(state.value,
                            { game.start(settings.value.level, modeMask = settings.value.modeMask); state.value = game.state }, {},
                            { game.home(); state.value = game.state }, settings.value,
                            onSelect = { settings.value = settings.value.copy(level = it) },
                            onToggleType = { val mask = settings.value.modeMask xor it.bit; if (mask != 0) settings.value = settings.value.copy(modeMask = mask) })
                    }
                }
            }
        }
    }
    @Test fun selectedReferenceHomeFitsAndHelpExplainsWithoutChangingSetup() {
        show()
        compose.onNodeWithTag("start").assertIsDisplayed()
        val start = compose.onNodeWithTag("start").fetchSemanticsNode()
        val viewport = compose.onNodeWithTag("design_viewport").fetchSemanticsNode().boundsInRoot
        // boundsInRoot is clipped; use the full layout rectangle to catch partial visibility.
        val position = start.positionInRoot
        assertTrue("Entire Start is visible without scrolling",
            position.x >= viewport.left && position.y >= viewport.top &&
                position.x + start.size.width <= viewport.right && position.y + start.size.height <= viewport.bottom)
        val visible = start.boundsInRoot
        assertEquals("Start left is not clipped", position.x, visible.left, 1f)
        assertEquals("Start top is not clipped", position.y, visible.top, 1f)
        assertEquals("Start right is not clipped", position.x + start.size.width, visible.right, 1f)
        assertEquals("Start bottom is not clipped", position.y + start.size.height, visible.bottom, 1f)
        capture("home-reference")
        compose.onNodeWithTag("help").performScrollTo().performClick()
        compose.onNodeWithTag("help").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("If several types match", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Colours:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This task requires", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(7, settings.value.modeMask); assertEquals(SessionScreen.HOME, game.state.screen)
        compose.onNodeWithTag("help").performScrollTo().performClick()
        compose.onNodeWithText("Colours:", substring = true).assertDoesNotExist()
    }
    @Test fun allSelectionsAndLevelsHaveAccurateExamplesAndGuardLastType() {
        show()
        for (mask in 1..7) for (level in 1..3) {
            compose.runOnIdle { settings.value = settings.value.copy(modeMask = mask, level = level) }
            for (type in StimulusType.entries) {
                compose.onNodeWithTag("type_${type.bit}").assert(if (mask and type.bit != 0) isToggleable().and(isOn()) else isToggleable().and(isOff()))
                if (mask == type.bit) compose.onNodeWithTag("type_${type.bit}").assertIsNotEnabled()
            }
            compose.onNodeWithTag("level_$level").assertIsSelected()
            compose.onAllNodes(hasTestTag("example_$level")).assertCountEquals(1)
            compose.onNodeWithTag("example_${level + 1}").assertDoesNotExist()
            compose.onNodeWithTag("start").performScrollTo().assertIsEnabled()
        }
    }
    @Test fun overviewShowsEveryTypeAndBaselineBeforeFullDefinitions() {
        show()
        compose.runOnIdle { game.start(modeMask = 7); time = 66000; game.advance(); state.value = game.state }
        for (tag in listOf("accuracy", "accuracy_2", "accuracy_4")) compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onAllNodesWithText("Hits: 0 of 6 · Misses: 6").assertCountEquals(3)
        compose.onNodeWithText("Accuracy includes", substring = true).assertIsDisplayed()
        capture("results-overview")
        compose.onNodeWithText("A hit is", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("result_details").performScrollTo().performClick()
        compose.onNodeWithText("A hit is a tap when Position matched.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Correct rejections: 14").assertCountEquals(3)
        assertEquals(SessionScreen.RESULTS, game.state.screen)
    }
    @Test fun homeReflowsAtLargeTextAndNarrowLandscape() {
        show()
        try {
            for ((w, h) in listOf(320 to 700, 760 to 360)) {
                val config = if (w > h) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                compose.activityRule.scenario.onActivity { it.requestedOrientation = if (w > h) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == config }
                compose.activityRule.scenario.onActivity { it.setContent { Fixture() } }
                for (font in listOf(1f, 2f)) {
                    compose.runOnIdle { width.value = w; height.value = h; scale.value = font }
                    for (type in StimulusType.entries) compose.onNodeWithTag("type_${type.bit}").performScrollTo()
                        .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                    compose.onNodeWithTag("level_3").performScrollTo().performClick().assertIsSelected()
                    compose.onNodeWithTag("start").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    compose.onNodeWithTag("practice").performScrollTo().assertIsDisplayed()
                    capture("home-${w}x$h-font-$font")
                }
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "f005-qa").apply { mkdirs() }
        compose.onNodeWithTag("design_viewport").captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
