package com.maswadkar.nback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maswadkar.nback.engine.storageCode
import com.maswadkar.nback.engine.SessionRules
import com.maswadkar.nback.engine.StimulusType
import com.maswadkar.nback.engine.SessionResult
import com.maswadkar.nback.engine.MonotonicClock
import com.maswadkar.nback.engine.SessionScreen
import com.maswadkar.nback.engine.VisualSession
import com.maswadkar.nback.engine.generateSequence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.random.Random
import java.util.UUID

/** Gameplay/navigation remain transient; accepted history writes belong to the application. */
class SessionViewModel(
    private val preferences: LevelSettings,
    val history: HistoryCoordinator,
    private val game: VisualSession = VisualSession.withConfig(MonotonicClock { SystemClock.elapsedRealtime() }) { type, config -> generateSequence(Random.Default, config.level, type.cardinality, config.sessionLength) },
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val completedNormal: (String) -> Unit = {},
) : ViewModel() {
    internal val homeUi = HomeUiState()
    var state by mutableStateOf(game.state)
        private set
    var settings by mutableStateOf(SettingsState())
        private set
    var historyNavigation by mutableStateOf(HistoryNavigation())
        private set
    var formatRevision by mutableIntStateOf(0)
        private set
    var resultId by mutableStateOf<String?>(null)
        private set
    private var selectedProgressGroup: ComparisonGroup? = null
    private var runId: String? = null
    val handlesBack: Boolean get() = historyNavigation.open || homeUi.destination != HomeDestination.HOME || state.screen != SessionScreen.HOME
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val tick = Runnable { refresh() }
    private data class Save(val version: Long, val level: Int, val modeMask: Int, val intervalSeconds: Int, val sessionLength: Int)
    private val saves = Channel<Save>(Channel.UNLIMITED)
    private var version = 0L

    init {
        viewModelScope.launch {
            for (save in saves) {
                try {
                    preferences.save(save.level, save.modeMask, save.intervalSeconds, save.sessionLength)
                    if (save.version == version) settings = settings.copy(saving = false)
                } catch (e: IOException) {
                    if (save.version == version) settings = settings.copy(saving = false, notice = SettingsNotice.SAVE_FAILED)
                }
            }
        }
        viewModelScope.launch {
            try {
                val loaded = preferences.load()
                val valid = loaded.level in 1..3
                val validTypes = loaded.modeMask in 1..7
                val resetLevel = loaded.reset || !valid
                val resetTypes = loaded.typesReset || !validTypes
                val validInterval = loaded.intervalSeconds in 1..30
                val resetInterval = loaded.intervalReset || !validInterval
                val validLength = loaded.sessionLength in SessionRules.LENGTHS
                val resetLength = loaded.lengthReset || !validLength
                settings = SettingsState(level = if (valid) loaded.level else 2, loading = false,
                    modeMask = if (validTypes) loaded.modeMask else 1,
                    intervalSeconds = if (validInterval) loaded.intervalSeconds else 3,
                    sessionLength = if (validLength) loaded.sessionLength else 20,
                    notice = when {
                        resetLength && (resetLevel || resetTypes || resetInterval) -> SettingsNotice.SETTINGS_RESET
                        resetLength -> SettingsNotice.LENGTH_RESET
                        resetInterval && (resetLevel || resetTypes) -> SettingsNotice.SETTINGS_RESET
                        resetInterval -> SettingsNotice.INTERVAL_RESET
                        resetLevel && resetTypes -> SettingsNotice.BOTH_RESET
                        resetLevel -> SettingsNotice.RESET
                        resetTypes -> SettingsNotice.TYPES_RESET
                        else -> null
                    })
                if (resetLevel || resetTypes || resetInterval || resetLength) queueSave()
            } catch (e: IOException) {
                settings = SettingsState(loading = false, notice = SettingsNotice.LOAD_FAILED)
            }
        }
    }

    fun selectLevel(level: Int) {
        require(level in 1..3)
        if (settings.loading || state.screen != SessionScreen.HOME || historyNavigation.open) return
        settings = settings.copy(level = level, notice = null)
        queueSave()
    }
    fun toggleType(type: StimulusType) {
        if (settings.loading || state.screen != SessionScreen.HOME || historyNavigation.open) return
        val mask = settings.modeMask xor type.bit
        if (mask == 0) return
        settings = settings.copy(modeMask = mask, notice = null)
        queueSave()
    }
    fun selectInterval(seconds: Int) {
        require(seconds in 1..30)
        if (settings.loading || state.screen != SessionScreen.HOME || historyNavigation.open) return
        settings = settings.copy(intervalSeconds = seconds, notice = null)
        queueSave()
    }
    fun selectLength(length: Int) {
        require(length in SessionRules.LENGTHS)
        if (settings.loading || state.screen != SessionScreen.HOME || historyNavigation.open) return
        settings = settings.copy(sessionLength = length, notice = null)
        queueSave()
    }
    fun openSettings() {
        if (state.screen == SessionScreen.HOME && !historyNavigation.open) homeUi.destination = HomeDestination.SETTINGS
    }
    fun openHelp() {
        if (state.screen == SessionScreen.HOME && !historyNavigation.open) homeUi.destination = HomeDestination.HELP
    }
    private fun queueSave() {
        settings = settings.copy(saving = true)
        check(saves.trySend(Save(++version, settings.level, settings.modeMask, settings.intervalSeconds, settings.sessionLength)).isSuccess)
    }
    fun retrySave() {
        if (!settings.loading && state.screen == SessionScreen.HOME && !historyNavigation.open) {
            settings = settings.copy(notice = null)
            queueSave()
        }
    }
    fun resume() { resumed = true; formatRevision++; refresh() }
    fun pauseTicker() { resumed = false; handler.removeCallbacks(tick) }
    fun start() {
        if (settings.loading || historyNavigation.open || game.state.isActive) return
        captureCompletion()
        val level = if (state.screen == SessionScreen.HOME) settings.level else state.config.level
        val practice = state.screen == SessionScreen.INTERRUPTED && state.config.practice
        game.start(level, practice, if (state.screen == SessionScreen.HOME) settings.modeMask else state.config.modeMask,
            if (state.screen == SessionScreen.HOME) settings.intervalSeconds else state.config.intervalSeconds,
            sessionLength = if (state.screen == SessionScreen.HOME) settings.sessionLength else state.config.sessionLength)
        homeUi.destination = HomeDestination.HOME
        runId = if (practice) null else newId()
        resultId = null
        refresh()
    }
    fun practice() {
        if (settings.loading || historyNavigation.open || game.state.isActive) return
        captureCompletion()
        game.start(if (state.screen == SessionScreen.HOME) settings.level else state.config.level, practice = true,
            modeMask = if (state.screen == SessionScreen.HOME) settings.modeMask else state.config.modeMask,
            intervalSeconds = if (state.screen == SessionScreen.HOME) settings.intervalSeconds else state.config.intervalSeconds,
            sessionLength = if (state.screen == SessionScreen.HOME) settings.sessionLength else state.config.sessionLength)
        homeUi.destination = HomeDestination.HOME
        runId = null; resultId = null
        refresh()
    }
    fun match(type: StimulusType = StimulusType.POSITION) { if (resumed && !historyNavigation.open) { game.match(type); refresh() } }
    fun nextExample(token: Long) { if (resumed) { game.nextExample(token); refresh() } }
    fun interrupt() { game.interrupt(); refresh() }
    fun back() {
        when {
            historyNavigation.confirmClear -> cancelClear()
            historyNavigation.detailId != null -> historyNavigation = historyNavigation.copy(detailId = null)
            historyNavigation.open -> historyNavigation = HistoryNavigation()
            homeUi.destination != HomeDestination.HOME -> { homeUi.destination = HomeDestination.HOME }
            state.isActive -> { game.interrupt(); refresh() }
            else -> home()
        }
    }
    fun home() {
        game.advance(); captureCompletion()
        game.home(); runId = null; resultId = null
        homeUi.destination = HomeDestination.HOME
        historyNavigation = HistoryNavigation(); refresh()
    }
    fun refresh() {
        handler.removeCallbacks(tick)
        game.advance(); captureCompletion()
        state = game.state
        if (resumed) game.millisUntilNextChange()?.let { handler.postDelayed(tick, it) }
    }
    private fun captureCompletion() {
        val current = game.state
        val id = runId ?: return
        if (current.screen != SessionScreen.RESULTS || current.config.practice || resultId == id) return
        val result = current.results[StimulusType.POSITION] ?: SessionResult()
        val colour = current.results[StimulusType.COLOUR] ?: SessionResult()
        val number = current.results[StimulusType.NUMBER] ?: SessionResult()
        val record = HistoryRecord(id, wallClock(), current.config.level, hits = result.hits,
            misses = result.misses, falseAlarms = result.falseAlarms, correctRejections = result.correctRejections,
            rulesVersion = 4, sessionLength = current.config.sessionLength, modeMask = current.config.modeMask,
            colourHits = colour.hits, colourMisses = colour.misses, colourFalseAlarms = colour.falseAlarms, colourCorrectRejections = colour.correctRejections,
            numberHits = number.hits, numberMisses = number.misses, numberFalseAlarms = number.falseAlarms, numberCorrectRejections = number.correctRejections,
            intervalSeconds = current.config.intervalSeconds,
            positionOutcomes = current.outcomes[StimulusType.POSITION]?.joinToString("") { it.storageCode().toString() } ?: "",
            colourOutcomes = current.outcomes[StimulusType.COLOUR]?.joinToString("") { it.storageCode().toString() } ?: "",
            numberOutcomes = current.outcomes[StimulusType.NUMBER]?.joinToString("") { it.storageCode().toString() } ?: "")
        history.capture(record)
        resultId = id
        completedNormal(id)
    }
    fun openHistory() {
        if (state.screen !in listOf(SessionScreen.HOME, SessionScreen.RESULTS) || historyNavigation.open) return
        historyNavigation = HistoryNavigation(open = true, group = selectedProgressGroup); history.reload()
    }
    fun filterHistory(level: Int) {
        require(level in 0..3)
        historyNavigation = historyNavigation.copy(filter = level, scrollIndex = 0, scrollOffset = 0)
    }
    fun filterHistoryMode(modeMask: Int) {
        require(modeMask in 0..7)
        historyNavigation = historyNavigation.copy(modeFilter = modeMask, scrollIndex = 0, scrollOffset = 0)
    }
    fun filterHistoryPace(pace: Int) {
        require(pace in 0..30)
        historyNavigation = historyNavigation.copy(paceFilter = pace, scrollIndex = 0, scrollOffset = 0)
    }
    fun filterHistoryLength(length: Int) {
        require(length == 0 || length in SessionRules.LENGTHS)
        historyNavigation = historyNavigation.copy(lengthFilter = length, scrollIndex = 0, scrollOffset = 0)
    }
    fun showProgress(progress: Boolean) {
        val selected = historyNavigation.group ?: history.state.records.firstOrNull()?.comparisonGroup()
        selectedProgressGroup = selected
        historyNavigation = historyNavigation.copy(progress = progress, group = selected)
    }
    fun toggleProgressTable() { historyNavigation = historyNavigation.copy(progressTable = !historyNavigation.progressTable) }
    fun selectGroup(group: ComparisonGroup) {
        selectedProgressGroup = group
        historyNavigation = historyNavigation.copy(group = group, progressIndex = 0, progressOffset = 0)
    }
    fun rememberProgressScroll(index: Int, offset: Int) {
        historyNavigation = historyNavigation.copy(progressIndex = index, progressOffset = offset)
    }
    fun rememberHistoryScroll(index: Int, offset: Int) {
        historyNavigation = historyNavigation.copy(scrollIndex = index, scrollOffset = offset)
    }
    fun openDetail(id: String) {
        if (history.state.load == HistoryLoad.READY && history.state.records.any { it.id == id })
            historyNavigation = historyNavigation.copy(detailId = id)
    }
    fun askClear() {
        if (historyNavigation.open && history.state.canClear) historyNavigation = historyNavigation.copy(confirmClear = true)
    }
    fun cancelClear() { historyNavigation = historyNavigation.copy(confirmClear = false) }
    fun confirmClear() { if (historyNavigation.confirmClear) { cancelClear(); history.clear() } }
    override fun onCleared() { handler.removeCallbacks(tick); saves.close() }
}

/** Retained by ViewModel, deliberately absent from saved-instance-state/process restoration. */
data class HistoryNavigation(
    val open: Boolean = false, val detailId: String? = null, val filter: Int = 0,
    val progress: Boolean = false, val progressTable: Boolean = false, val group: ComparisonGroup? = null,
    val progressIndex: Int = 0, val progressOffset: Int = 0,
    val paceFilter: Int = 0, val lengthFilter: Int = 0,
    val modeFilter: Int = 0, val scrollIndex: Int = 0, val scrollOffset: Int = 0, val confirmClear: Boolean = false,
)
