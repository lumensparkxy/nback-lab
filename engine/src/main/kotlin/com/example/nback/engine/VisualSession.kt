package com.example.nback.engine

import kotlin.random.Random

fun interface MonotonicClock { fun nowMillis(): Long }

object SessionRules {
    const val SCORED_TRIALS = 20
    const val MATCHES = 6
    const val TRIAL_MS = 3_000L
    const val HIGHLIGHT_MS = 1_000L
}

data class SessionConfig(val level: Int = 2, val practice: Boolean = false) {
    init { require(level in 1..3) }
    val scoredTrials: Int get() = if (practice) 4 else SessionRules.SCORED_TRIALS
    val totalTrials: Int get() = level + scoredTrials
    val durationMillis: Long get() = totalTrials * SessionRules.TRIAL_MS
}

/** Cells are 0..8. Exactly six uniformly sampled scored indices are matches. */
fun generateSequence(random: Random, level: Int = 2): List<Int> {
    val config = SessionConfig(level)
    val matches = (level until config.totalTrials).shuffled(random).take(SessionRules.MATCHES).toSet()
    val cells = mutableListOf<Int>()
    repeat(config.totalTrials) { trial ->
        cells += when {
            trial < level -> random.nextInt(9)
            trial in matches -> cells[trial - level]
            else -> random.nextInt(8).let { if (it >= cells[trial - level]) it + 1 else it }
        }
    }
    return cells.toList()
}

fun practiceSequence(level: Int): List<Int> = when (level) {
    1 -> listOf(1, 1, 5, 5, 9)
    2 -> listOf(1, 5, 1, 9, 1, 4)
    3 -> listOf(1, 5, 9, 1, 2, 9, 4)
    else -> throw IllegalArgumentException("Supported levels are 1, 2 and 3")
}.map { it - 1 }

enum class SessionScreen { HOME, PLAYING, INTERRUPTED, RESULTS, PRACTICE_FEEDBACK, PRACTICE_COMPLETE }
enum class Outcome { HIT, MISS, FALSE_ALARM, CORRECT_REJECTION }
fun classify(matches: Boolean, responded: Boolean): Outcome = when {
    matches && responded -> Outcome.HIT
    matches -> Outcome.MISS
    responded -> Outcome.FALSE_ALARM
    else -> Outcome.CORRECT_REJECTION
}

data class SessionResult(val hits: Int = 0, val misses: Int = 0, val falseAlarms: Int = 0, val correctRejections: Int = 0) {
    val correct: Int get() = hits + correctRejections
    val accuracy: Int get() = correct * 100 / SessionRules.SCORED_TRIALS
    fun record(outcome: Outcome): SessionResult = when (outcome) {
        Outcome.HIT -> copy(hits = hits + 1)
        Outcome.MISS -> copy(misses = misses + 1)
        Outcome.FALSE_ALARM -> copy(falseAlarms = falseAlarms + 1)
        Outcome.CORRECT_REJECTION -> copy(correctRejections = correctRejections + 1)
    }
}

data class PracticeFeedback(val token: Long, val currentCell: Int, val referenceCell: Int, val responded: Boolean) {
    val matches: Boolean get() = currentCell == referenceCell
    val outcome: Outcome get() = classify(matches, responded)
}

data class SessionState(
    val screen: SessionScreen = SessionScreen.HOME,
    val trial: Int = 0,
    val highlightedCell: Int? = null,
    val responseRecorded: Boolean = false,
    val result: SessionResult? = null,
    val config: SessionConfig = SessionConfig(),
    val feedback: PracticeFeedback? = null,
) {
    val isWarmUp: Boolean get() = trial <= config.level
    val progress: Int get() = if (isWarmUp) trial else trial - config.level
    val canRespond: Boolean get() = screen == SessionScreen.PLAYING && !isWarmUp && !responseRecorded
    val isActive: Boolean get() = screen == SessionScreen.PLAYING || screen == SessionScreen.PRACTICE_FEEDBACK
}

/** Single-thread confined; clock, sequences and all gameplay rules are Android-free. */
class VisualSession(private val clock: MonotonicClock, private val sequenceFactory: (Int) -> List<Int>) {
    var state = SessionState()
        private set
    private var positions = emptyList<Int>()
    private var responses = BooleanArray(0)
    private var origin = 0L
    private var elapsed = 0L
    private var nextToScore = 0
    private var counts = SessionResult()
    private var practiceIndex = 0
    // Never reset across runs: old Next callbacks cannot affect a restarted practice.
    private var feedbackToken = 0L

    fun start(level: Int = 2, practice: Boolean = false) {
        val config = SessionConfig(level, practice)
        if (state.isActive) return
        val sequence = (if (practice) practiceSequence(level) else sequenceFactory(level)).toList()
        require(sequence.size == config.totalTrials && sequence.all { it in 0..8 })
        if (!practice) require((level until sequence.size).count { sequence[it] == sequence[it - level] } == SessionRules.MATCHES)
        positions = sequence
        responses = BooleanArray(sequence.size)
        nextToScore = level
        counts = SessionResult()
        practiceIndex = 0
        origin = clock.nowMillis()
        elapsed = 0
        state = SessionState(SessionScreen.PLAYING, trial = 1, highlightedCell = sequence[0], config = config)
    }

    fun advance() {
        if (state.screen != SessionScreen.PLAYING) return
        elapsed = (clock.nowMillis() - origin).coerceAtLeast(elapsed)
        if (state.config.practice) advancePractice() else advanceNormal()
    }

    private fun advanceNormal() {
        val config = state.config
        val closed = (elapsed / SessionRules.TRIAL_MS).coerceAtMost(config.totalTrials.toLong()).toInt()
        while (nextToScore < closed) {
            counts = counts.record(classify(positions[nextToScore] == positions[nextToScore - config.level], responses[nextToScore]))
            nextToScore++
        }
        if (elapsed >= config.durationMillis) {
            state = SessionState(SessionScreen.RESULTS, result = counts, config = config)
        } else renderTrial(closed)
    }

    private fun advancePractice() {
        val config = state.config
        val index = if (practiceIndex == 0) (elapsed / SessionRules.TRIAL_MS).coerceAtMost(config.level.toLong()).toInt() else practiceIndex
        val deadline = if (practiceIndex == 0) (config.level + 1) * SessionRules.TRIAL_MS else SessionRules.TRIAL_MS
        if (elapsed >= deadline) {
            val feedback = PracticeFeedback(++feedbackToken, positions[index], positions[index - config.level], responses[index])
            state = state.copy(screen = if (index == positions.lastIndex) SessionScreen.PRACTICE_COMPLETE else SessionScreen.PRACTICE_FEEDBACK,
                trial = index + 1, highlightedCell = null, responseRecorded = responses[index], feedback = feedback)
        } else renderTrial(index)
    }

    private fun renderTrial(index: Int) {
        state = state.copy(trial = index + 1,
            highlightedCell = positions[index].takeIf { elapsed % SessionRules.TRIAL_MS < SessionRules.HIGHLIGHT_MS },
            responseRecorded = responses[index], feedback = null)
    }

    fun match() {
        advance()
        if (!state.canRespond) return
        responses[state.trial - 1] = true
        state = state.copy(responseRecorded = true)
    }

    fun nextExample(token: Long) {
        if (state.screen != SessionScreen.PRACTICE_FEEDBACK || state.feedback?.token != token) return
        practiceIndex = state.trial
        origin = clock.nowMillis()
        elapsed = 0
        state = state.copy(screen = SessionScreen.PLAYING, trial = practiceIndex + 1,
            highlightedCell = positions[practiceIndex], responseRecorded = false, feedback = null)
    }

    fun interrupt() {
        advance()
        if (state.isActive) {
            state = SessionState(SessionScreen.INTERRUPTED, config = state.config)
            positions = emptyList()
            responses.fill(false)
        }
    }

    fun home() {
        state = SessionState()
        positions = emptyList()
        responses.fill(false)
    }

    /** Scheduling only wakes the engine; elapsed time always comes from the clock. */
    fun millisUntilNextChange(): Long? {
        if (state.screen != SessionScreen.PLAYING) return null
        val phase = elapsed % SessionRules.TRIAL_MS
        return if (phase < SessionRules.HIGHLIGHT_MS) SessionRules.HIGHLIGHT_MS - phase else SessionRules.TRIAL_MS - phase
    }
}
