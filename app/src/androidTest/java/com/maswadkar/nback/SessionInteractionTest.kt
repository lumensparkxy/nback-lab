package com.maswadkar.nback

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import com.maswadkar.nback.engine.MonotonicClock
import com.maswadkar.nback.engine.SessionScreen
import com.maswadkar.nback.engine.VisualSession
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var time = 0L
    private val fontScale = mutableStateOf(1f)
    private val sequence = listOf(1, 5, 1, 9, 2, 9, 4, 6, 4, 8, 3, 7, 3, 2, 5, 2, 8, 6, 9, 6, 1, 4).map { it - 1 }
    private val game = VisualSession(MonotonicClock { time }) { sequence }
    private val state = mutableStateOf(game.state)
    private fun publish() { state.value = game.state }
    private fun at(t: Long) { compose.runOnIdle { time = t; game.advance(); publish() } }

    @Before fun show() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) { MaterialTheme { SessionContent(state.value,
            { game.start(); publish() }, { game.match(); publish() }, { game.home(); publish() }) } } }
    }

    @Test fun gridRemainsFixedAcrossWarmupPromptAndAcknowledgementAtLargeFont() {
        compose.runOnIdle { fontScale.value = 2f }
        compose.onNodeWithTag("start").performScrollTo().performClick()
        val warmupBounds = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        at(6_000)
        val promptBounds = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("match").performClick()
        val recordedBounds = compose.onNodeWithTag("grid").fetchSemanticsNode().boundsInRoot
        assertEquals("Warm-up to scored grid bounds", warmupBounds, promptBounds)
        assertEquals("Acknowledgement must not move grid", promptBounds, recordedBounds)
    }

    @CriticalCi @Test fun warmupNeutralFeedbackDuplicateInputAndNewTrial() {
        compose.onNodeWithTag("start").performScrollTo().performClick()
        compose.onNodeWithTag("match").assertIsNotEnabled()
        at(6_000)
        compose.onNodeWithTag("match").assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        val response = compose.onNodeWithTag("match").fetchSemanticsNode().config
        if (response.contains(SemanticsProperties.StateDescription)) {
            assertEquals("✓ Recorded", response[SemanticsProperties.StateDescription])
        } else compose.onNodeWithText("✓ Response recorded").assertIsDisplayed()
        compose.onNodeWithTag("match").assertIsNotEnabled().performTouchInput { click() }
        at(9_000); compose.onNodeWithTag("match").assertIsEnabled()
        compose.onNodeWithContentDescription("Visual position grid").assertIsDisplayed()
        at(66_000); assertEquals(1, game.state.result!!.hits)
        assertEquals(0, game.state.result!!.falseAlarms)
    }

    @CriticalCi @Test fun holdingDoesNotRespondOrRepeatAndReleaseBelongsToNewTrial() {
        compose.onNodeWithTag("start").performScrollTo().performClick()
        at(8_999)
        compose.onNodeWithTag("match").performTouchInput { down(center) }
        compose.runOnIdle { assertFalse(game.state.responseRecorded) }
        at(9_001)
        compose.runOnIdle { assertFalse(game.state.responseRecorded) }
        compose.onNodeWithTag("match").performTouchInput { up() }
        compose.runOnIdle { assertEquals(4, game.state.trial); assertTrue(game.state.responseRecorded) }
        at(66_000)
        assertEquals(0, game.state.result!!.hits)
        assertEquals(1, game.state.result!!.falseAlarms)
    }

    @Test fun workedExampleRendersResultsAndSecondSessionResets() {
        repeat(2) { session ->
            compose.onNodeWithTag(if (session == 0) "start" else "play_again").performScrollTo().performClick()
            val origin = session * 66_000L
            for (trial in listOf(3, 4, 6, 8, 9, 13, 15)) {
                at(origin + (trial - 1) * 3_000L + 500)
                compose.onNodeWithTag("match").performClick()
            }
            at(origin + 66_000)
            compose.onNodeWithText("75% accuracy").assertIsDisplayed()
            compose.onNodeWithTag("result_details").performScrollTo().performClick()
            compose.onNodeWithText("Correct: 15/20").performScrollTo().assertIsDisplayed()
            for (label in listOf("Hits: 4 of 6", "Misses: 2", "False alarms: 3 of 14", "Correct rejections: 11")) {
                compose.onAllNodesWithText(label).onLast().performScrollTo().assertIsDisplayed()
            }
        }
        compose.onNodeWithTag("home").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SessionScreen.HOME, game.state.screen); assertNull(game.state.result) }
    }
}
