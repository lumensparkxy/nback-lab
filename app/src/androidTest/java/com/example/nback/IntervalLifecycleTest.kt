package com.example.nback

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.nback.engine.SessionScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IntervalLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun intervalSnapshotSurvivesRecreationInterruptionAndRestart() {
        val model = compose.activity.session
        compose.waitUntil(10000) { !model.settings.loading }
        val original = model.settings.intervalSeconds
        try {
            compose.runOnIdle { model.home(); model.selectInterval(8); model.start(); model.selectInterval(30) }
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertSame(model, compose.activity.session)
                assertEquals(8, model.state.config.intervalSeconds)
                assertEquals(2000L, model.state.config.exposureMillis)
                assertEquals(8, model.settings.intervalSeconds)
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle {
                assertEquals(SessionScreen.INTERRUPTED, model.state.screen)
                model.start(); assertEquals(8, model.state.config.intervalSeconds)
                assertEquals(1, model.state.trial)
                model.home(); model.selectInterval(16); model.practice()
                assertEquals(16, model.state.config.intervalSeconds)
                assertEquals(3000L, model.state.config.exposureMillis)
            }
        } finally {
            compose.runOnIdle { model.home(); model.selectInterval(original) }
            compose.waitUntil(10000) { !model.settings.saving }
            compose.runOnIdle { assertNotEquals(SettingsNotice.SAVE_FAILED, model.settings.notice) }
        }
    }
}
