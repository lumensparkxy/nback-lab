package com.example.nback

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nback.engine.SessionScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun start(level: Int = 2) {
        compose.waitUntil(10_000) { !compose.activity.session.settings.loading }
        compose.onNodeWithTag("level_$level").performScrollTo().performClick()
        compose.onNodeWithTag("start").performScrollTo().performClick()
    }

    @Test fun realOneAndThreeBackSessionsCompleteAtTheirOwnLevels() {
        for (level in listOf(1, 3)) {
            start(level)
            val model = compose.activity.session
            assertEquals(level, model.state.config.level)
            compose.waitUntil(80_000) { model.state.screen == SessionScreen.RESULTS }
            compose.onNodeWithText("70% accuracy").assertIsDisplayed()
            assertEquals(6, model.state.result!!.misses)
            compose.onNodeWithTag("home").performScrollTo().performClick()
        }
    }

    @Test fun practiceFeedbackSurvivesRotationButBackAndBackgroundInterrupt() {
        compose.waitUntil(10_000) { !compose.activity.session.settings.loading }
        compose.onNodeWithTag("level_1").performScrollTo().performClick()
        compose.onNodeWithTag("practice").performScrollTo().performClick()
        val model = compose.activity.session
        compose.waitUntil(10_000) { model.state.screen == SessionScreen.PRACTICE_FEEDBACK }
        val feedback = model.state.feedback
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertSame(model, compose.activity.session); assertEquals(feedback, model.state.feedback) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Practice interrupted").assertIsDisplayed()
        compose.onNodeWithTag("restart").performClick()
        compose.onNodeWithText("Warm-up 1/1").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Practice interrupted").assertIsDisplayed()
        compose.onNodeWithTag("home").performClick()
        compose.onNodeWithTag("practice").performScrollTo().performClick()
        compose.onNodeWithTag("skip").performClick()
        compose.onNodeWithTag("level_1").performScrollTo().assertIsDisplayed()
    }

    @Test fun instructionsAndWarmupAreAccessible() {
        compose.onNodeWithText("Set up your next round").assertIsDisplayed()
        compose.onNodeWithTag("start").performScrollTo().assertIsDisplayed()
        start()
        compose.onNodeWithText("Warm-up 1/2").assertIsDisplayed()
        compose.onNodeWithTag("match").assertIsNotEnabled()
    }

    @Test fun recreationKeepsTheSameSessionDuringHighlightAndBlank() {
        start()
        val retained = compose.activity.session
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            assertSame(retained, compose.activity.session)
            assertEquals(SessionScreen.PLAYING, retained.state.screen)
        }
        compose.waitUntil(5_000) { retained.state.highlightedCell == null }
        val trial = retained.state.trial
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            assertSame(retained, compose.activity.session)
            assertEquals(SessionScreen.PLAYING, retained.state.screen)
            assertTrue(retained.state.trial >= trial)
        }
    }

    @Test fun actualOrientationChangeRetainsSession() {
        start()
        val retained = compose.activity.session
        compose.waitUntil(10_000) { retained.state.canRespond }
        compose.onNodeWithTag("match").performClick()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.runOnIdle {
            assertSame(retained, compose.activity.session)
            assertEquals(SessionScreen.PLAYING, retained.state.screen)
            assertTrue(retained.state.trial >= 3)
            if (retained.state.trial == 3) assertTrue(retained.state.responseRecorded)
        }
        compose.onNodeWithTag("grid").assertIsDisplayed()
        compose.onNodeWithTag("match").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    @Test fun pauseWithoutStopInterruptsAndRestartBeginsWarmup() {
        start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Session interrupted").assertIsDisplayed()
        compose.onNodeWithTag("restart").performClick()
        compose.onNodeWithText("Warm-up 1/2").assertIsDisplayed()
        compose.runOnIdle { assertNull(compose.activity.session.state.result) }
    }

    @Test fun backInterruptsAndHomeReturnsToInstructions() {
        start()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Session interrupted").assertIsDisplayed()
        compose.onNodeWithTag("home").performClick()
        compose.onNodeWithText("Set up your next round").assertIsDisplayed()
    }

    @Test fun realTimedSessionCompletesAndResultsSurviveRecreationAndBackground() {
        start()
        val retained = compose.activity.session
        compose.waitUntil(72_000) { retained.state.screen == SessionScreen.RESULTS }
        compose.onNodeWithText("70% accuracy").assertIsDisplayed()
        val result = retained.state.result
        assertEquals(0, result!!.hits); assertEquals(6, result.misses)
        assertEquals(0, result.falseAlarms); assertEquals(14, result.correctRejections)
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(result, compose.activity.session.state.result) }
        compose.onNodeWithTag("play_again").performScrollTo().performClick()
        compose.onNodeWithText("Warm-up 1/2").assertIsDisplayed()
        compose.runOnIdle { assertNull(retained.state.result) }
    }
}
