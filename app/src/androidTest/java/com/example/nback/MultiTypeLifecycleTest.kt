package com.example.nback

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.nback.engine.SessionScreen
import com.example.nback.engine.StimulusType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MultiTypeLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun combinedConfigurationSurvivesRecreationAndInterruptsAsOneSession() {
        val model = compose.activity.session
        compose.waitUntil(10000) { !model.settings.loading }
        val original = model.settings
        try {
            compose.runOnIdle {
                model.home(); model.selectLevel(1)
                StimulusType.entries.filter { model.settings.modeMask and it.bit == 0 }.forEach(model::toggleType)
                model.start()
            }
            compose.waitUntil(10000) { model.state.trial >= 2 }
            compose.runOnIdle { model.match(StimulusType.COLOUR); model.match(StimulusType.NUMBER) }
            val recordedTrial = model.state.trial
            val recordedTypes = model.state.recordedTypes
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertSame(model, compose.activity.session)
                assertEquals(7, model.state.config.modeMask); assertEquals(1, model.state.config.level)
                assertEquals(SessionScreen.PLAYING, model.state.screen)
                assertEquals(setOf(StimulusType.COLOUR, StimulusType.NUMBER), recordedTypes)
                // A real-clock recreation may cross a deadline. It must retain the
                // current responses or advance normally, never reset/extend the run.
                assertTrue(model.state.trial >= recordedTrial)
                if (model.state.trial == recordedTrial) assertEquals(recordedTypes, model.state.recordedTypes)
                else assertTrue(model.state.recordedTypes.isEmpty())
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle {
                assertEquals(SessionScreen.INTERRUPTED, model.state.screen); assertTrue(model.state.results.isEmpty())
                model.start(); assertEquals(7, model.state.config.modeMask); assertEquals(1, model.state.trial)
                model.home(); model.practice()
            }
            compose.waitUntil(12000) { model.state.screen == SessionScreen.PRACTICE_FEEDBACK }
            val feedback = model.state.feedback
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.runOnIdle {
                assertSame(model, compose.activity.session); assertEquals(feedback, model.state.feedback)
                assertEquals(3, model.state.feedback!!.types.size)
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle { assertEquals(SessionScreen.INTERRUPTED, model.state.screen); assertTrue(model.state.config.practice) }
        } finally {
            compose.runOnIdle {
                model.home(); model.selectLevel(original.level)
                // Enable original types first so the nonempty guard cannot prevent restoration.
                StimulusType.entries.filter { original.modeMask and it.bit != 0 && model.settings.modeMask and it.bit == 0 }.forEach(model::toggleType)
                StimulusType.entries.filter { original.modeMask and it.bit == 0 && model.settings.modeMask and it.bit != 0 }.forEach(model::toggleType)
            }
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
