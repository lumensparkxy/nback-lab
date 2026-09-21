package com.example.nback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nback.engine.MonotonicClock
import com.example.nback.engine.SessionScreen
import com.example.nback.engine.VisualSession
import com.example.nback.engine.generateSequence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.random.Random
import java.util.UUID

/** Gameplay/navigation remain transient; accepted history writes belong to the application. */
class SessionViewModel(
    private val preferences: LevelSettings,
    val history: HistoryCoordinator,
    private val game: VisualSession = VisualSession(MonotonicClock { SystemClock.elapsedRealtime() }) { n -> generateSequence(Random.Default, n) },
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
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
    private var runId: String? = null
    val handlesBack: Boolean get() = historyNavigation.open || state.screen != SessionScreen.HOME
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val tick = Runnable { refresh() }
    private data class Save(val version: Long, val level: Int)
    private val saves = Channel<Save>(Channel.UNLIMITED)
    private var version = 0L

    init {
        viewModelScope.launch {
            for (save in saves) {
                try {
                    preferences.save(save.level)
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
                settings = SettingsState(level = if (valid) loaded.level else 2, loading = false,
                    notice = if (loaded.reset || !valid) SettingsNotice.RESET else null)
                if (loaded.reset || !valid) queueSave()
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
    private fun queueSave() {
        settings = settings.copy(saving = true)
        check(saves.trySend(Save(++version, settings.level)).isSuccess)
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
        game.start(level, practice)
        runId = if (practice) null else newId()
        resultId = null
        refresh()
    }
    fun practice() {
        if (settings.loading || historyNavigation.open || game.state.isActive) return
        captureCompletion()
        game.start(if (state.screen == SessionScreen.HOME) settings.level else state.config.level, practice = true)
        runId = null; resultId = null
        refresh()
    }
    fun match() { if (resumed && !historyNavigation.open) { game.match(); refresh() } }
    fun nextExample(token: Long) { if (resumed) { game.nextExample(token); refresh() } }
    fun interrupt() { game.interrupt(); refresh() }
    fun back() {
        when {
            historyNavigation.confirmClear -> cancelClear()
            historyNavigation.detailId != null -> historyNavigation = historyNavigation.copy(detailId = null)
            historyNavigation.open -> historyNavigation = HistoryNavigation()
            state.isActive -> { game.interrupt(); refresh() }
            else -> home()
        }
    }
    fun home() {
        game.advance(); captureCompletion()
        game.home(); runId = null; resultId = null
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
        val result = requireNotNull(current.result)
        val record = HistoryRecord(id, wallClock(), current.config.level, hits = result.hits,
            misses = result.misses, falseAlarms = result.falseAlarms, correctRejections = result.correctRejections)
        history.capture(record)
        resultId = id
    }
    fun openHistory() {
        if (state.screen !in listOf(SessionScreen.HOME, SessionScreen.RESULTS) || historyNavigation.open) return
        historyNavigation = HistoryNavigation(open = true); history.reload()
    }
    fun filterHistory(level: Int) {
        require(level in 0..3)
        historyNavigation = historyNavigation.copy(filter = level, scrollIndex = 0, scrollOffset = 0)
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
    val scrollIndex: Int = 0, val scrollOffset: Int = 0, val confirmClear: Boolean = false,
)
