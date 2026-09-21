package com.example.nback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
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

/** Retains active gameplay in memory; only the selected level is stored. */
class SessionViewModel(
    private val preferences: LevelSettings,
    private val game: VisualSession = VisualSession(MonotonicClock { SystemClock.elapsedRealtime() }) { n -> generateSequence(Random.Default, n) },
) : ViewModel() {
    var state by mutableStateOf(game.state)
        private set
    var settings by mutableStateOf(SettingsState())
        private set
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
        if (settings.loading || state.screen != SessionScreen.HOME) return
        settings = settings.copy(level = level, notice = null)
        queueSave()
    }
    private fun queueSave() {
        settings = settings.copy(saving = true)
        check(saves.trySend(Save(++version, settings.level)).isSuccess)
    }
    fun retrySave() {
        if (!settings.loading && state.screen == SessionScreen.HOME) {
            settings = settings.copy(notice = null)
            queueSave()
        }
    }
    fun resume() { resumed = true; refresh() }
    fun pauseTicker() { resumed = false; handler.removeCallbacks(tick) }
    fun start() {
        if (settings.loading) return
        val level = if (state.screen == SessionScreen.HOME) settings.level else state.config.level
        val practice = state.screen == SessionScreen.INTERRUPTED && state.config.practice
        game.start(level, practice); refresh()
    }
    fun practice() {
        if (settings.loading) return
        game.start(if (state.screen == SessionScreen.HOME) settings.level else state.config.level, practice = true)
        refresh()
    }
    fun match() { if (resumed) { game.match(); refresh() } }
    fun nextExample(token: Long) { if (resumed) { game.nextExample(token); refresh() } }
    fun interrupt() { game.interrupt(); refresh() }
    fun back() { if (state.isActive) game.interrupt() else game.home(); refresh() }
    fun home() { game.home(); refresh() }
    fun refresh() {
        handler.removeCallbacks(tick)
        game.advance()
        state = game.state
        if (resumed) game.millisUntilNextChange()?.let { handler.postDelayed(tick, it) }
    }
    override fun onCleared() { handler.removeCallbacks(tick); saves.close() }
}
