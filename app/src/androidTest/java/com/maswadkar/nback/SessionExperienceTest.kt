package com.maswadkar.nback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.maswadkar.nback.engine.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.random.Random

class SessionExperienceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val holder=ViewModelStore()
    private val job=SupervisorJob()
    private val store=MemoryHistoryStore()
    private lateinit var model:SessionViewModel
    private lateinit var history:HistoryCoordinator
    private var time=0L
    private var failSave=false
    private var failRead=false
    private val font=mutableStateOf(1f)
    private fun launch(records:List<HistoryRecord> = emptyList()) {
        records.forEach { store.records[it.id]=it }
        compose.runOnUiThread {
            history=HistoryCoordinator(object:HistoryStore {
                override suspend fun load():List<HistoryRecord> { if(failRead)throw IOException();return store.load() }
                override suspend fun save(record:HistoryRecord) { if(failSave)throw IOException();store.save(record) }
                override suspend fun clear()=store.clear()
            },CoroutineScope(job+Dispatchers.Main.immediate))
            model=SessionViewModel(object:LevelSettings {
                override suspend fun load()=LoadedLevel()
                override suspend fun save(level:Int,modeMask:Int,intervalSeconds:Int,sessionLength:Int)=Unit
            },history,VisualSession.withConfig(MonotonicClock { time }) { type,config ->
                generateSequence(Random(type.bit),config.level,type.cardinality,config.sessionLength)
            }).also { holder.put("model",it);it.resume() }
        }
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,font.value)) { NBackApp(model) } }
        compose.waitUntil(10000) { !model.settings.loading && history.state.load==HistoryLoad.READY }
    }
    @After fun close() { compose.runOnUiThread { holder.clear();job.cancel() } }
    private fun click(tag:String) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun progress() {
        click("history")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("progress_tab").fetchSemanticsNodes().isNotEmpty() }
        click("progress_tab")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("group_menu").fetchSemanticsNodes().isNotEmpty() }
    }
    @CriticalCi @Test fun settingsHelpAndVariableCompletionUseSameSnapshotAndSaveOnce() {
        launch()
        compose.onNodeWithTag("level_2").assertDoesNotExist()
        click("settings")
        for(length in SessionRules.LENGTHS) { click("length_$length");assertEquals(length,model.settings.sessionLength) }
        click("length_10");click("type_2");click("type_4");click("level_3")
        compose.onNodeWithTag("interval").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        click("settings_back");click("help")
        compose.onNodeWithTag("help_example_3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Number differs. Leave its match button unanswered.").performScrollTo().assertIsDisplayed()
        capture("help")
        click("help_back");click("start")
        assertEquals(SessionConfig(3,modeMask=7,intervalSeconds=1,sessionLength=10),model.state.config)
        compose.runOnIdle { time=3000;model.refresh() }
        compose.onNodeWithText("Turn 1/10").assertIsDisplayed()
        compose.runOnIdle { time=13000;model.refresh() }
        compose.waitUntil(10000) { history.state.records.size==1 }
        assertEquals(10,history.state.records.single().sessionLength)
        assertEquals(4,history.state.records.single().rulesVersion)
        compose.onAllNodesWithText("Hits: 0 of 3 · Misses: 3").assertCountEquals(3)
        click("timeline_table")
        compose.onNodeWithTag("timeline_turn_10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("timeline_turn_11").assertDoesNotExist()
        capture("length10-result")
        click("play_again");assertEquals(10,model.state.config.sessionLength)
        compose.runOnIdle { model.home();model.selectLength(50);model.practice() }
        assertEquals(4,model.state.config.scoredTrials)
        assertEquals(50,model.state.config.sessionLength)
        assertEquals(1,history.state.records.size)
    }
    @Test fun progressKeepsComparableGroupsLegacyDetailsAndTableNavigation() {
        val rows=listOf(sampleRecord("old",time=1000),sampleRecord("new",time=2000).copy(hits=5,misses=1,falseAlarms=1,correctRejections=13),
            lengthRecord("other",50,1).copy(completedAt=500))
        launch(rows);progress()
        compose.onNodeWithText("Saved sessions: 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("Latest Position accuracy: 90%"))
        capture("progress")
        compose.onNodeWithTag("progress_list").performScrollToNode(hasTestTag("progress_table"));click("progress_table")
        compose.onNodeWithTag("progress_list").performScrollToKey("old")
        compose.onNodeWithTag("progress_record_old").performClick()
        compose.onNodeWithText("Timeline unavailable for this older session.").performScrollTo().assertIsDisplayed()
        click("history_back")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("progress_record_old").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress_record_old").assertIsDisplayed()
        compose.onNodeWithTag("progress_record_other").assertDoesNotExist()
        compose.runOnIdle { font.value=2f }
        capture("progress-table-font2")
        compose.onNodeWithTag("progress_list").performScrollToIndex(0)
        click("sessions_tab")
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("length_filter_menu"));click("length_filter_menu");click("length_filter_50")
        compose.onNodeWithTag("history_list").performScrollToNode(hasTestTag("record_other"))
        compose.onNodeWithTag("record_old").assertDoesNotExist()
    }
    @Test fun tenThousandProgressRowsStayLazyAndReadFailureHidesChart() {
        launch((0 until 10000).map { sampleRecord("r$it",time=1750000000000+it) });progress()
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("Sessions in this group: 10000"))
        compose.onNodeWithTag("progress_list").performScrollToNode(hasTestTag("progress_table"));click("progress_table")
        compose.onNodeWithTag("progress_list").performScrollToKey("r9999")
        compose.onNodeWithTag("progress_record_r9999").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size<100)
        compose.runOnIdle { failRead=true;history.reload() }
        compose.waitUntil(10000) { history.state.load==HistoryLoad.FAILED }
        compose.onNodeWithTag("progress_chart").assertDoesNotExist()
        compose.onNodeWithTag("progress_list").performScrollToNode(hasTestTag("retry_load"))
        compose.runOnIdle { failRead=false }
        click("retry_load")
        compose.waitUntil(10000) { history.state.load==HistoryLoad.READY }
    }
    @Test fun failedSavesDoNotBecomeProgressUntilRetryCommitsAndClearRemovesThem() {
        launch();failSave=true
        compose.runOnIdle { model.selectLength(10);model.selectInterval(1);model.start();time=12000;model.refresh() }
        compose.waitUntil(10000) { history.state.failed==1 }
        assertTrue(history.state.records.isEmpty())
        click("history");click("progress_tab")
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("Saved sessions: 0"))
        compose.runOnIdle { failSave=false;history.retry() }
        compose.waitUntil(10000) { history.state.records.size==1 }
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("Saved sessions: 1"))
        compose.runOnIdle { history.retry();history.clear() }
        compose.waitUntil(10000) { history.state.records.isEmpty() && !history.state.clearing }
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("Saved sessions: 0"))
        assertEquals(10,model.settings.sessionLength)
    }
    @Test fun selectedGroupSurvivesHomeSettingsAndMissingGroupIsNotSilentlyReplaced() {
        val old=lengthRecord("old",50,1).copy(completedAt=1000)
        val newer=lengthRecord("new",10,1).copy(completedAt=2000)
        launch(listOf(old,newer));progress()
        compose.runOnIdle { model.selectGroup(old.comparisonGroup());model.home();model.openSettings();model.selectLength(30);model.back();model.openHistory();model.showProgress(true) }
        assertEquals(old.comparisonGroup(),model.historyNavigation.group)
        compose.runOnIdle { store.records.remove("old");history.reload() }
        compose.waitUntil(10000) { history.state.load==HistoryLoad.READY }
        compose.onNodeWithTag("progress_list").performScrollToNode(hasText("This comparison group is no longer available. Choose another group."))
        assertEquals(old.comparisonGroup(),model.historyNavigation.group)
    }

    @Test fun thirtyAndFiftyTurnResultsReopenWithCorrectDenominatorsAndRounding() {
        val thirty=lengthRecord("thirty",30,1).copy(hits=6,misses=3,falseAlarms=7,correctRejections=14,
            positionOutcomes="H".repeat(6)+"M".repeat(3)+"F".repeat(7)+"C".repeat(14))
        val fifty=lengthRecord("fifty",50,1)
        launch(listOf(thirty,fifty))
        for(record in listOf(thirty,fifty)) {
            compose.runOnIdle { model.home();model.openHistory();model.openDetail(record.id) }
            compose.waitUntil(10000) { history.state.load==HistoryLoad.READY }
            compose.runOnIdle { model.openDetail(record.id) }
            compose.onNodeWithText("${if(record.sessionLength==30) 67 else 70}% accuracy").assertIsDisplayed()
            compose.onNodeWithText("Hits: ${record.hits} of ${record.sessionLength*3/10} · Misses: ${record.misses}").assertIsDisplayed()
            click("timeline_table")
            compose.onNodeWithTag("timeline_turn_${record.sessionLength}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("timeline_turn_${record.sessionLength+1}").assertDoesNotExist()
            click("history_back")
        }
    }

    @Test fun settingsHelpBackAndPlayAgainNeverInvokeResultHomeOpportunity() {
        val state=mutableStateOf(SessionState())
        var adHome=0;var normalHome=0;var starts=0
        compose.setContent { NBackTheme { SessionContent(state.value, { starts++ }, {}, { normalHome++ },
            SettingsState(loading=false), onResultsHome={ adHome++ }) } }
        click("settings");click("settings_back");click("help");click("help_back")
        assertEquals(0,adHome);assertEquals(0,normalHome)
        compose.runOnIdle { state.value=SessionState(SessionScreen.RESULTS,results=mapOf(StimulusType.POSITION to SessionResult(0,6,0,14))) }
        click("play_again");assertEquals(1,starts);assertEquals(0,adHome)
        click("home");assertEquals(1,adHome)
        compose.runOnIdle { state.value=SessionState(SessionScreen.INTERRUPTED) }
        click("home");assertEquals(1,normalHome);assertEquals(1,adHome)
    }

    @CriticalCi @Test fun equalContentRefreshFinishesPreparingEmptyAndPopulatedHistory() {
        val records=mutableStateOf<List<HistoryRecord>>(emptyList(),androidx.compose.runtime.referentialEqualityPolicy())
        compose.setContent {
            val prepared=preparedHistory(records.value,HistoryNavigation())
            androidx.compose.material3.Text(if(prepared==null) "Preparing" else "Ready: ${prepared.rows.size}")
        }
        fun ready(count:Int) = compose.waitUntil(10000) {
            compose.onAllNodesWithText("Ready: $count").fetchSemanticsNodes().isNotEmpty()
        }
        ready(0)
        compose.runOnIdle { records.value=ArrayList() }
        ready(0)
        compose.runOnIdle { records.value=listOf(sampleRecord("same")) }
        ready(1)
        compose.runOnIdle { records.value=ArrayList(records.value) }
        ready(1)
    }

    private fun capture(name:String) {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"f008-qa").apply { mkdirs() }
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
