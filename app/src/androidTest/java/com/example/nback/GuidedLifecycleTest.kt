package com.example.nback

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GuidedLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "f005-qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun helpRetainsInMemoryAcrossRotationAndFreshOwnerStartsCollapsed() {
        val model = compose.activity.session
        compose.waitUntil(10000) { !model.settings.loading }
        compose.runOnIdle {
            model.home(); model.homeUi.helpExpanded = false; model.selectLevel(2)
            com.example.nback.engine.StimulusType.entries.forEach { if (model.settings.modeMask and it.bit == 0) model.toggleType(it) }
        }
        compose.onNodeWithTag("start").assertIsDisplayed()
        capture("activity-home")
        compose.onNodeWithTag("help").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("help").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.runOnIdle {
            assertSame(model, compose.activity.session)
            // Process recreation constructs a fresh owner; no saved-state input carries this UI flag.
            val holder = ViewModelStore()
            val fresh = SessionViewModel(object : LevelSettings {
                override suspend fun load() = LoadedLevel()
                override suspend fun save(level: Int, modeMask: Int) = Unit
            }, model.history)
            holder.put("fresh", fresh)
            assertFalse(fresh.homeUi.helpExpanded)
            holder.clear()
        }
        compose.onNodeWithTag("help").performClick()
    }
    @Test fun savedDetailsRotateAndLargeTextHistoryRemainsUsable() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val originalScale = shell("settings --user current get system font_scale")
        val model = compose.activity.session
        compose.waitUntil(10000) { !model.settings.loading && model.history.state.load == HistoryLoad.READY }
        val record = multiRecord("guided-layout-${System.nanoTime()}", 7)
        compose.runOnIdle { model.home(); model.history.capture(record) }
        compose.waitUntil(10000) { model.history.state.entries[record.id]?.status == SaveStatus.SAVED }
        compose.onNodeWithTag("history").performScrollTo().performClick()
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_${record.id}"))
        capture("activity-history")
        compose.onNodeWithTag("record_${record.id}").performClick()
        capture("activity-detail")
        compose.onNodeWithTag("result_details").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("result_details").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("A hit is a tap when Number matched.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("result_details").performScrollTo().performClick()
        try {
            shell("settings --user current put system font_scale 2.0")
            compose.waitUntil(10000) { compose.activity.resources.configuration.fontScale >= 1.9f }
            for ((orientation, config) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
                compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == config }
                for (tag in listOf("accuracy", "accuracy_2", "accuracy_4")) compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                capture("detail-font2-$config")
                compose.runOnIdle { model.back() }
                compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("filter_0"))
                compose.onNodeWithTag("filter_3").assertIsDisplayed().performClick().assertIsSelected()
                capture("history-font2-$config")
                compose.onNodeWithTag("filter_0").performClick()
                compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_${record.id}"))
                compose.onNodeWithTag("record_${record.id}").performClick()
                compose.onNodeWithTag("result_details").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            }
        } finally {
            if (originalScale == "null") shell("settings --user current delete system font_scale") else shell("settings --user current put system font_scale $originalScale")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
