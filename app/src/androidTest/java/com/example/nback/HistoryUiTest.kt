package com.example.nback

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val job = SupervisorJob()
    private val holder = ViewModelStore()
    private val rows = MemoryHistoryStore()
    private var readFailure = false
    private var saveFailure = false
    private var clearFailure = false
    private lateinit var history: HistoryCoordinator
    private lateinit var model: SessionViewModel
    private fun launch(records: List<HistoryRecord> = emptyList()) {
        records.forEach { rows.records[it.id] = it }
        compose.runOnUiThread {
            history = HistoryCoordinator(object : HistoryStore {
                override suspend fun load(): List<HistoryRecord> { if (readFailure) throw IOException(); return rows.load() }
                override suspend fun save(record: HistoryRecord) { if (saveFailure) throw IOException(); rows.save(record) }
                override suspend fun clear() { if (clearFailure) throw IOException(); rows.clear() }
            }, CoroutineScope(job + Dispatchers.Main.immediate))
            model = SessionViewModel(object : LevelSettings {
                override suspend fun load() = LoadedLevel(3)
                override suspend fun save(level: Int, modeMask: Int) = Unit
            }, history).also { holder.put("model", it); it.resume() }
        }
        compose.setContent { NBackApp(model) }
        compose.waitUntil { !model.settings.loading && history.state.load == HistoryLoad.READY }
    }
    @After fun close() { compose.runOnUiThread { holder.clear(); job.cancel() } }
    private fun open() {
        compose.onNodeWithTag("history").performScrollTo().performClick()
        compose.waitUntil { history.state.load == HistoryLoad.READY }
    }
    private fun openClearMenu() {
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("history_actions"))
        compose.onNodeWithTag("history_actions").assertIsDisplayed().performClick()
    }
    @Test fun emptyFiltersClearCancelAndGlobalClearRetainDifficulty() {
        launch(listOf(sampleRecord("a", 1), sampleRecord("b", 3)))
        open()
        compose.onNodeWithTag("filter_2").performScrollTo().performClick()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("No saved 2-back sessions yet"))
        compose.onNodeWithText("No saved 2-back sessions yet").performScrollTo().assertIsDisplayed()
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsDisplayed().performClick()
        compose.onNodeWithText("Clear all history?").assertIsDisplayed()
        compose.onNodeWithTag("cancel_clear").performClick()
        assertEquals(2, rows.records.size)
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsDisplayed().performClick()
        compose.runOnIdle { model.back() }
        assertEquals(2, rows.records.size)
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsDisplayed().performClick()
        compose.onNodeWithTag("confirm_clear").performClick()
        compose.waitUntil { history.state.records.isEmpty() }
        assertTrue(rows.records.isEmpty()); assertEquals(3, model.settings.level)
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsNotEnabled()
        compose.onNodeWithTag("clear_history").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        compose.onNodeWithTag("home").performScrollTo().performClick()
        open(); compose.onNodeWithTag("filter_0").assertIsSelected()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("No saved sessions yet"))
        compose.onNodeWithText("No saved sessions yet").performScrollTo().assertIsDisplayed()
    }
    @Test fun tenThousandRowsStayLazyAndDetailBackRetainsFilterAndScroll() {
        launch((0 until 10_000).map { sampleRecord("r$it", it % 3 + 1, 1_750_000_000_000L + it) })
        open()
        compose.onNodeWithTag("filter_3").performScrollTo().performClick()
        // Address stable lazy-list keys without assuming a header count or
        // linearly paging through 10,000 rows on slower emulator targets.
        compose.onNodeWithTag("history_list").performScrollToKey("session:r9698")
        compose.onNodeWithTag("record_r9698").performClick()
        compose.onNodeWithText("Hits: 4 of 6 · Misses: 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("False alarms: 3 of 14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accuracy includes correctly waiting on non-matches. Not tapping at all gives 70% accuracy, so check hits and misses too.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { model.back() }
        compose.onNodeWithTag("record_r9698").assertIsDisplayed()
        assertEquals(3, model.historyNavigation.filter)
        assertTrue(model.historyNavigation.scrollIndex >= 100)
        compose.onNodeWithTag("history_list").performScrollToIndex(0)
        compose.onNodeWithTag("filter_0").performClick()
        compose.onNodeWithTag("history_list").performScrollToKey("session:r0")
        compose.onNodeWithTag("record_r0").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().let { assertTrue("Lazy list must not compose 10000 rows", it.size < 100) }
    }
    @Test fun exactModeFiltersExcludeOtherCombinationsAndRetainAcrossDetail() {
        launch(listOf(sampleRecord("position", 2), multiRecord("pair", 3), multiRecord("triple", 7), multiRecord("other-level", 3).copy(level = 1)))
        open()
        compose.onNodeWithTag("mode_filter_menu").performScrollTo().performClick()
        compose.onNodeWithTag("mode_filter_3").performScrollTo().performClick()
        compose.onNodeWithTag("filter_2").performScrollTo().performClick()
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_pair"))
        compose.onNodeWithTag("record_pair").performClick()
        compose.onNodeWithTag("accuracy_2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("accuracy_4").assertDoesNotExist()
        compose.runOnIdle { model.back() }
        assertEquals(3, model.historyNavigation.modeFilter); assertEquals(2, model.historyNavigation.filter)
        compose.onNodeWithTag("record_position").assertDoesNotExist()
        compose.onNodeWithTag("record_triple").assertDoesNotExist()
        compose.onNodeWithTag("record_other-level").assertDoesNotExist()
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsDisplayed().performClick()
        compose.onNodeWithTag("confirm_clear").performClick()
        compose.waitUntil { rows.records.isEmpty() }
        compose.onNodeWithTag("home").performScrollTo().performClick(); open()
        assertEquals(0, model.historyNavigation.modeFilter)
    }
    @Test fun failedReadsHideRowsAndClearAndRetryRestoresThem() {
        launch(listOf(sampleRecord()))
        open()
        compose.runOnIdle { readFailure = true; history.reload() }
        compose.waitUntil { history.state.load == HistoryLoad.FAILED }
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsNotEnabled()
        compose.onNodeWithTag("clear_history").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        compose.onNodeWithTag("record_one").assertDoesNotExist()
        compose.onNodeWithTag("history_list").performScrollToNode(hasText("Couldn’t load history."))
        compose.onNodeWithText("Couldn’t load history.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { readFailure = false }
        compose.onNodeWithTag("retry_load").performScrollTo().performClick()
        compose.waitUntil { history.state.load == HistoryLoad.READY }
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_one"))
        compose.onNodeWithTag("record_one").performClick()
        compose.runOnIdle { history.clear() }
        compose.waitUntil { history.state.records.isEmpty() }
        compose.onNodeWithText("This result is no longer in history").assertIsDisplayed()
    }
    @Test fun failedSaveRemainsRetryableOnHomeAndClearFailureNeedsFreshConfirmation() {
        launch()
        compose.runOnIdle { saveFailure = true; history.capture(sampleRecord()) }
        compose.waitUntil { history.state.failed == 1 }
        compose.onNodeWithText("Unsaved results may be lost if the app closes.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { saveFailure = false }
        compose.onNodeWithTag("retry_saving").performScrollTo().performClick()
        compose.waitUntil { history.state.entries["one"]?.status == SaveStatus.SAVED }
        open()
        compose.runOnIdle { clearFailure = true }
        openClearMenu(); compose.onNodeWithTag("clear_history").assertIsDisplayed().performClick()
        compose.onNodeWithTag("confirm_clear").performClick()
        compose.waitUntil { history.state.clearFailed }
        compose.onNodeWithText("Couldn’t clear history").performScrollTo().assertIsDisplayed()
        assertEquals(1, rows.records.size)
        compose.runOnIdle { clearFailure = false }
        compose.onNodeWithTag("retry_clear").performScrollTo().performClick()
        compose.onNodeWithText("Clear all history?").assertIsDisplayed()
        compose.onNodeWithTag("confirm_clear").performClick()
        compose.waitUntil { rows.records.isEmpty() }
    }
    @Test fun localeClockPreferenceAndRepeatedDstTimesRenderDistinctOffsets() {
        val first = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli()
        val second = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
        val zurich = HistoryTime(Locale.US, ZoneId.of("Europe/Zurich"), true)
        assertTrue(zurich.format(first, true).endsWith("UTC+02:00"))
        assertTrue(zurich.format(second, true).endsWith("UTC+01:00"))
        assertTrue(zurich.format(first).contains("02:30"))
        assertTrue(HistoryTime(Locale.US, ZoneId.of("Europe/Zurich"), false).format(first).contains("AM"))
        assertNotEquals(zurich.format(first), HistoryTime(Locale.GERMANY, ZoneId.of("UTC"), true).format(first))
    }
}
