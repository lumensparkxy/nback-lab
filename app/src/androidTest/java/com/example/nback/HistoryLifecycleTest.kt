package com.example.nback

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HistoryLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun largeTextLandscapeConfirmationExplainsTheWholeDeletion() {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        fun capture(name: String) {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val directory = java.io.File(context.getExternalFilesDir(null), "f004-qa").apply { mkdirs() }
            automation.takeScreenshot().let { bitmap ->
                java.io.File(directory, "$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        val originalScale = shell("settings --user current get system font_scale")
        try {
            val model = compose.activity.session
            compose.waitUntil(10_000) { !model.settings.loading && model.history.state.load == HistoryLoad.READY }
            compose.runOnIdle { model.history.capture(sampleRecord("large-font-${System.nanoTime()}")); model.openHistory() }
            compose.waitUntil { model.history.state.canClear }
            shell("settings --user current put system font_scale 2.0")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                val config = compose.activity.resources.configuration
                config.fontScale == 2f && config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            compose.runOnIdle { model.askClear() }
            compose.onNodeWithTag("confirm_clear").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("clear_explanation").assertHeightIsAtLeast(96.dp)
            capture("history-clear-before-scroll")
            val explanation = compose.onNodeWithTag("clear_explanation")
            val range = explanation.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
            if (range.maxValue() > 0f) {
                explanation.performTouchInput { swipeUp() }
                compose.waitUntil { range.value() > 0f }
                explanation.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                    it(0f, range.maxValue())
                }
            }
            val bodyText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.clear_body)
            val body = compose.onNodeWithText(bodyText)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            body.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val bodyY = body.fetchSemanticsNode().positionInRoot.y
            val viewport = explanation.fetchSemanticsNode().boundsInRoot
            assertTrue("The final line must be reachable, whether content fits or scrolls",
                bodyY + layout.getLineTop(layout.lineCount - 1) >= viewport.top - 1 &&
                    bodyY + layout.getLineBottom(layout.lineCount - 1) <= viewport.bottom + 1)
            capture("history-clear-after-scroll")
            compose.onNodeWithTag("cancel_clear").performClick()
        } finally {
            if (originalScale == "null") shell("settings --user current delete system font_scale")
            else shell("settings --user current put system font_scale $originalScale")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    @Test fun historyDetailAndConfirmationSurviveRecreationAndBackground() {
        val model = compose.activity.session
        compose.waitUntil(10_000) { !model.settings.loading && model.history.state.load == HistoryLoad.READY }
        val record = sampleRecord("rotation-fixture-${System.nanoTime()}", 2)
        compose.runOnIdle { model.history.capture(record) }
        compose.waitUntil(10_000) { model.history.state.entries[record.id]?.status == SaveStatus.SAVED }
        compose.onNodeWithTag("history").performScrollTo().performClick()
        compose.waitUntil { model.history.state.load == HistoryLoad.READY }
        compose.onNodeWithTag("filter_2").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("filter_2").assertIsSelected()
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_${record.id}"))
        compose.onNodeWithTag("record_${record.id}").performClick()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("75% accuracy").assertIsDisplayed()
        compose.runOnIdle { assertSame(model, compose.activity.session); assertEquals(record.id, model.historyNavigation.detailId) }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("clear_history").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Clear all history?").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Clear all history?").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Visual n-back").assertIsDisplayed()
    }
}
