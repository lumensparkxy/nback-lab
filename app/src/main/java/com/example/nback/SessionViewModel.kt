package com.example.nback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.nback.engine.MonotonicClock
import com.example.nback.engine.SessionScreen
import com.example.nback.engine.VisualSession
import com.example.nback.engine.generateSequence
import kotlin.random.Random

/** Retained only in memory: configuration recreation keeps it; process loss does not. */
class SessionViewModel(
    private val game: VisualSession = VisualSession(MonotonicClock { SystemClock.elapsedRealtime() }) {
        generateSequence(Random.Default)
    },
) : ViewModel() {
    var state by mutableStateOf(game.state)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val tick = Runnable { refresh() }

    fun resume() { resumed = true; refresh() }
    fun pauseTicker() { resumed = false; handler.removeCallbacks(tick) }
    fun start() { game.start(); refresh() }
    fun match() { if (resumed) { game.match(); refresh() } }
    fun interrupt() { game.interrupt(); refresh() }
    fun back() {
        if (state.screen == SessionScreen.PLAYING) game.interrupt() else game.home()
        refresh()
    }
    fun home() { game.home(); refresh() }

    fun refresh() {
        handler.removeCallbacks(tick)
        game.advance()
        state = game.state
        if (resumed) game.millisUntilNextChange()?.let { handler.postDelayed(tick, it) }
    }

    override fun onCleared() { handler.removeCallbacks(tick) }
}
