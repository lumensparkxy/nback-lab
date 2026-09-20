package com.example.nback.engine

import kotlin.random.Random

fun interface MonotonicClock {
    fun nowMillis(): Long
}

object SessionRules {
    const val LEVEL = 2
    const val SCORED_TRIALS = 20
    const val TOTAL_TRIALS = LEVEL + SCORED_TRIALS
    const val MATCHES = 6
    const val TRIAL_MS = 3_000L
    const val HIGHLIGHT_MS = 1_000L
    const val DURATION_MS = TOTAL_TRIALS * TRIAL_MS
}

/** Generate the complete sequence before play; cells are indexed 0 through 8. */
fun generateSequence(random: Random): List<Int> {
    val matches = (SessionRules.LEVEL until SessionRules.TOTAL_TRIALS)
        .shuffled(random).take(SessionRules.MATCHES).toSet()
    val cells = mutableListOf<Int>()
    repeat(SessionRules.TOTAL_TRIALS) { trial ->
        cells += when {
            trial < SessionRules.LEVEL -> random.nextInt(9)
            trial in matches -> cells[trial - SessionRules.LEVEL]
            else -> {
                val forbidden = cells[trial - SessionRules.LEVEL]
                val choice = random.nextInt(8)
                if (choice >= forbidden) choice + 1 else choice
            }
        }
    }
    return cells.toList()
}

enum class SessionScreen { HOME, PLAYING, INTERRUPTED, RESULTS }

data class SessionResult(
    val hits: Int = 0,
    val misses: Int = 0,
    val falseAlarms: Int = 0,
    val correctRejections: Int = 0,
) {
    val correct: Int get() = hits + correctRejections
    val accuracy: Int get() = correct * 100 / SessionRules.SCORED_TRIALS
}

data class SessionState(
    val screen: SessionScreen = SessionScreen.HOME,
    val trial: Int = 0,
    val highlightedCell: Int? = null,
    val responseRecorded: Boolean = false,
    val result: SessionResult? = null,
) {
    val isWarmUp: Boolean get() = trial <= SessionRules.LEVEL
    val progress: Int get() = if (isWarmUp) trial else trial - SessionRules.LEVEL
    val canRespond: Boolean get() = screen == SessionScreen.PLAYING && !isWarmUp && !responseRecorded
}

/** Single-thread confined state machine. Android/UI dependencies deliberately absent. */
class VisualSession(
    private val clock: MonotonicClock,
    private val sequenceFactory: () -> List<Int>,
) {
    var state: SessionState = SessionState()
        private set
    private var positions = emptyList<Int>()
    private var responses = BooleanArray(SessionRules.TOTAL_TRIALS)
    private var origin = 0L
    private var elapsed = 0L
    private var nextToScore = SessionRules.LEVEL
    private var counts = SessionResult()

    fun start() {
        if (state.screen == SessionScreen.PLAYING) return
        val sequence = sequenceFactory().toList()
        require(sequence.size == SessionRules.TOTAL_TRIALS && sequence.all { it in 0..8 })
        require((SessionRules.LEVEL until sequence.size).count {
            sequence[it] == sequence[it - SessionRules.LEVEL]
        } == SessionRules.MATCHES)
        positions = sequence
        responses = BooleanArray(SessionRules.TOTAL_TRIALS)
        nextToScore = SessionRules.LEVEL
        counts = SessionResult()
        // Generation time is not charged to the first trial.
        origin = clock.nowMillis()
        elapsed = 0
        state = SessionState(SessionScreen.PLAYING, trial = 1, highlightedCell = positions[0])
    }

    fun advance() {
        if (state.screen != SessionScreen.PLAYING) return
        elapsed = (clock.nowMillis() - origin).coerceAtLeast(elapsed)
        val closedTrials = (elapsed / SessionRules.TRIAL_MS).coerceAtMost(SessionRules.TOTAL_TRIALS.toLong()).toInt()
        while (nextToScore < closedTrials) {
            val match = positions[nextToScore] == positions[nextToScore - SessionRules.LEVEL]
            val response = responses[nextToScore]
            counts = when {
                match && response -> counts.copy(hits = counts.hits + 1)
                match -> counts.copy(misses = counts.misses + 1)
                response -> counts.copy(falseAlarms = counts.falseAlarms + 1)
                else -> counts.copy(correctRejections = counts.correctRejections + 1)
            }
            nextToScore++
        }
        if (elapsed >= SessionRules.DURATION_MS) {
            state = SessionState(screen = SessionScreen.RESULTS, result = counts)
            return
        }
        val index = closedTrials
        state = SessionState(
            screen = SessionScreen.PLAYING,
            trial = index + 1,
            highlightedCell = positions[index].takeIf { elapsed % SessionRules.TRIAL_MS < SessionRules.HIGHLIGHT_MS },
            responseRecorded = responses[index],
        )
    }

    fun match() {
        advance()
        if (!state.canRespond) return
        responses[state.trial - 1] = true
        state = state.copy(responseRecorded = true)
    }

    fun interrupt() {
        advance()
        if (state.screen == SessionScreen.PLAYING) {
            state = SessionState(screen = SessionScreen.INTERRUPTED)
            positions = emptyList()
            responses.fill(false)
        }
    }

    fun home() {
        state = SessionState()
        positions = emptyList()
        responses.fill(false)
    }

    /** Call after advance; scheduling merely wakes the engine, never supplies elapsed time. */
    fun millisUntilNextChange(): Long? {
        if (state.screen != SessionScreen.PLAYING) return null
        val phase = elapsed % SessionRules.TRIAL_MS
        return if (phase < SessionRules.HIGHLIGHT_MS) SessionRules.HIGHLIGHT_MS - phase else SessionRules.TRIAL_MS - phase
    }
}
