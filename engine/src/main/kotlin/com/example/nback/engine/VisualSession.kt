package com.example.nback.engine

import kotlin.random.Random

fun interface MonotonicClock { fun nowMillis(): Long }

object SessionRules {
    const val SCORED_TRIALS = 20
    const val MATCHES = 6
    const val TRIAL_MS = 3_000L
    const val HIGHLIGHT_MS = 1_000L
}

enum class StimulusType(val bit: Int, val cardinality: Int) {
    POSITION(1, 9), COLOUR(2, 6), NUMBER(4, 9),
}
fun activeTypes(modeMask: Int): List<StimulusType> {
    require(modeMask in 1..7)
    return StimulusType.entries.filter { modeMask and it.bit != 0 }
}

data class SessionConfig(val level: Int = 2, val practice: Boolean = false, val modeMask: Int = 1) {
    init { require(level in 1..3); require(modeMask in 1..7) }
    val types: List<StimulusType> get() = activeTypes(modeMask)
    val scoredTrials: Int get() = if (practice) 4 else SessionRules.SCORED_TRIALS
    val totalTrials: Int get() = level + scoredTrials
    val durationMillis: Long get() = totalTrials * SessionRules.TRIAL_MS
}

/** Cells are 0..8. Exactly six uniformly sampled scored indices are matches. */
fun generateSequence(random: Random, level: Int = 2, cardinality: Int = 9): List<Int> {
    require(cardinality >= 2)
    val config = SessionConfig(level)
    val matches = (level until config.totalTrials).shuffled(random).take(SessionRules.MATCHES).toSet()
    val cells = mutableListOf<Int>()
    repeat(config.totalTrials) { trial ->
        cells += when {
            trial < level -> random.nextInt(cardinality)
            trial in matches -> cells[trial - level]
            else -> random.nextInt(cardinality - 1).let { if (it >= cells[trial - level]) it + 1 else it }
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

data class TypeFeedback(val current: Int, val reference: Int, val responded: Boolean) {
    val matches: Boolean get() = current == reference
    val outcome: Outcome get() = classify(matches, responded)
}
data class PracticeFeedback(val token: Long, val types: Map<StimulusType, TypeFeedback>) {
    // Position-only compatibility for the original contract fixtures.
    val currentCell: Int get() = types.getValue(StimulusType.POSITION).current
    val referenceCell: Int get() = types.getValue(StimulusType.POSITION).reference
    val responded: Boolean get() = types.getValue(StimulusType.POSITION).responded
    val matches: Boolean get() = types.getValue(StimulusType.POSITION).matches
    val outcome: Outcome get() = types.getValue(StimulusType.POSITION).outcome
}

data class SessionState(
    val screen: SessionScreen = SessionScreen.HOME,
    val trial: Int = 0,
    val stimulus: Map<StimulusType, Int> = emptyMap(),
    val recordedTypes: Set<StimulusType> = emptySet(),
    val results: Map<StimulusType, SessionResult> = emptyMap(),
    val config: SessionConfig = SessionConfig(),
    val feedback: PracticeFeedback? = null,
) {
    val highlightedCell: Int? get() = stimulus[StimulusType.POSITION]
    val responseRecorded: Boolean get() = StimulusType.POSITION in recordedTypes
    val result: SessionResult? get() = results[StimulusType.POSITION]
    val isWarmUp: Boolean get() = trial <= config.level
    val progress: Int get() = if (isWarmUp) trial else trial - config.level
    val canRespond: Boolean get() = canRespond(StimulusType.POSITION)
    fun canRespond(type: StimulusType): Boolean = screen == SessionScreen.PLAYING && !isWarmUp &&
        type in config.types && type !in recordedTypes
    val isActive: Boolean get() = screen == SessionScreen.PLAYING || screen == SessionScreen.PRACTICE_FEEDBACK
}

fun practiceSequences(config: SessionConfig): Map<StimulusType, List<Int>> = config.types.associateWith { type ->
    if (config.modeMask == 1) practiceSequence(config.level) else {
        val values = MutableList(config.level) { it % type.cardinality }
        repeat(4) { step ->
            val rank = config.types.indexOf(type)
            val target = if (config.types.size == 1) step % 2 == 0 else when (step) {
                0 -> false
                1 -> rank == 0
                2 -> true
                else -> if (config.types.size == 2) rank == 1 else rank < 2
            }
            val reference = values[step]
            values += if (target) reference else (reference + 1) % type.cardinality
        }
        values.toList()
    }
}

/** Single-thread confined; clock, sequences and all gameplay rules are Android-free. */
class VisualSession private constructor(
    private val clock: MonotonicClock,
    private val sequenceFactory: (StimulusType, Int) -> List<Int>,
) {
    constructor(clock: MonotonicClock, sequenceFactory: (Int) -> List<Int>) : this(clock,
        { type, n -> if (type == StimulusType.POSITION) sequenceFactory(n) else generateSequence(Random.Default, n, type.cardinality) })
    companion object {
        fun withTypes(clock: MonotonicClock, factory: (StimulusType, Int) -> List<Int>) = VisualSession(clock, factory)
    }
    var state = SessionState()
        private set
    private var streams = emptyMap<StimulusType, List<Int>>()
    private var responses = emptyMap<StimulusType, BooleanArray>()
    private var origin = 0L
    private var elapsed = 0L
    private var nextToScore = 0
    private var counts = emptyMap<StimulusType, SessionResult>()
    private var practiceIndex = 0
    // Never reset across runs: old Next callbacks cannot affect a restarted practice.
    private var feedbackToken = 0L

    fun start(level: Int = 2, practice: Boolean = false, modeMask: Int = 1) {
        val config = SessionConfig(level, practice, modeMask)
        if (state.isActive) return
        val sequences = if (practice) practiceSequences(config) else config.types.associateWith { sequenceFactory(it, level).toList() }
        sequences.forEach { (type, sequence) ->
            require(sequence.size == config.totalTrials && sequence.all { it in 0 until type.cardinality })
            if (!practice) require((level until sequence.size).count { sequence[it] == sequence[it - level] } == SessionRules.MATCHES)
        }
        streams = sequences
        responses = config.types.associateWith { BooleanArray(config.totalTrials) }
        nextToScore = level
        counts = config.types.associateWith { SessionResult() }
        practiceIndex = 0
        origin = clock.nowMillis()
        elapsed = 0
        state = SessionState(SessionScreen.PLAYING, config = config)
        renderTrial(0)
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
            counts = counts.mapValues { (type, result) ->
                val values = streams.getValue(type)
                result.record(classify(values[nextToScore] == values[nextToScore - config.level], responses.getValue(type)[nextToScore]))
            }
            nextToScore++
        }
        if (elapsed >= config.durationMillis) {
            state = SessionState(SessionScreen.RESULTS, results = counts, config = config)
        } else renderTrial(closed)
    }

    private fun advancePractice() {
        val config = state.config
        val index = if (practiceIndex == 0) (elapsed / SessionRules.TRIAL_MS).coerceAtMost(config.level.toLong()).toInt() else practiceIndex
        val deadline = if (practiceIndex == 0) (config.level + 1) * SessionRules.TRIAL_MS else SessionRules.TRIAL_MS
        if (elapsed >= deadline) {
            val feedback = PracticeFeedback(++feedbackToken, config.types.associateWith { type ->
                TypeFeedback(streams.getValue(type)[index], streams.getValue(type)[index - config.level], responses.getValue(type)[index])
            })
            state = state.copy(screen = if (index == config.totalTrials - 1) SessionScreen.PRACTICE_COMPLETE else SessionScreen.PRACTICE_FEEDBACK,
                trial = index + 1, stimulus = emptyMap(), recordedTypes = recordedAt(index), feedback = feedback)
        } else renderTrial(index)
    }

    private fun renderTrial(index: Int) {
        state = state.copy(trial = index + 1,
            stimulus = if (elapsed % SessionRules.TRIAL_MS < SessionRules.HIGHLIGHT_MS) streams.mapValues { it.value[index] } else emptyMap(),
            recordedTypes = recordedAt(index), feedback = null)
    }

    private fun recordedAt(index: Int) = responses.filterValues { it[index] }.keys.toSet()

    fun match(type: StimulusType = StimulusType.POSITION) {
        advance()
        if (!state.canRespond(type)) return
        responses.getValue(type)[state.trial - 1] = true
        state = state.copy(recordedTypes = recordedAt(state.trial - 1))
    }

    fun nextExample(token: Long) {
        if (state.screen != SessionScreen.PRACTICE_FEEDBACK || state.feedback?.token != token) return
        practiceIndex = state.trial
        origin = clock.nowMillis()
        elapsed = 0
        state = state.copy(screen = SessionScreen.PLAYING, trial = practiceIndex + 1,
            stimulus = streams.mapValues { it.value[practiceIndex] }, recordedTypes = emptySet(), feedback = null)
    }

    fun interrupt() {
        advance()
        if (state.isActive) {
            state = SessionState(SessionScreen.INTERRUPTED, config = state.config)
            streams = emptyMap()
            responses = emptyMap()
        }
    }

    fun home() {
        state = SessionState()
        streams = emptyMap()
        responses = emptyMap()
    }

    /** Scheduling only wakes the engine; elapsed time always comes from the clock. */
    fun millisUntilNextChange(): Long? {
        if (state.screen != SessionScreen.PLAYING) return null
        val phase = elapsed % SessionRules.TRIAL_MS
        return if (phase < SessionRules.HIGHLIGHT_MS) SessionRules.HIGHLIGHT_MS - phase else SessionRules.TRIAL_MS - phase
    }
}
