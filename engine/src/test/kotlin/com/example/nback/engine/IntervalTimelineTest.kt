package com.example.nback.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IntervalTimelineTest {
    @Test fun allIntervalsUseApprovedExposureAndExactResponseDeadlines() {
        for (seconds in 1..30) for (n in 1..3) for (mask in 1..7) {
            var now = 0L
            val game = VisualSession.withTypes(MonotonicClock { now }) { type, level -> generateSequence(Random(type.bit), level, type.cardinality) }
            game.start(n, modeMask = mask, intervalSeconds = seconds)
            val config = game.state.config
            val expectedExposure = when { seconds <= 7 -> 1000L; seconds <= 15 -> 2000L; else -> 3000L }
            assertEquals(expectedExposure, config.exposureMillis)
            assertEquals(expectedExposure, game.millisUntilNextChange())
            now = expectedExposure - 1; game.advance(); assertFalse(game.state.stimulus.isEmpty())
            now++; game.advance()
            assertEquals(seconds == 1, game.state.stimulus.isNotEmpty())
            game.home(); now = 0; game.start(n, modeMask = mask, intervalSeconds = seconds)
            now = n * config.intervalMillis - 1; game.match(config.types.first()); assertTrue(game.state.recordedTypes.isEmpty())
            now++; game.match(config.types.first()); assertEquals(n + 1, game.state.trial)
            assertEquals(setOf(config.types.first()), game.state.recordedTypes)
            game.match(config.types.first()); assertEquals(1, game.state.recordedTypes.size)
            now = config.durationMillis; game.match(config.types.first())
            assertEquals(SessionScreen.RESULTS, game.state.screen)
            for (type in config.types) {
                val outcomes = game.state.outcomes.getValue(type)
                assertEquals(20, outcomes.size)
                assertEquals(game.state.results.getValue(type), outcomes.fold(SessionResult()) { a, b -> a.record(b) })
                val points = accuracyTimeline(config, outcomes)
                assertEquals((n + 1) * config.intervalMillis, points.first().elapsedMillis)
                assertEquals(config.durationMillis, points.last().elapsedMillis)
                assertEquals(game.state.results.getValue(type).accuracy.toDouble(), points.last().percentage, 0.0)
            }
            val completed = game.state; now += 90000; game.advance(); assertEquals(completed, game.state)
        }
    }
    @Test fun delayedCallbacksKeepOrderedOutcomesAndScheduledChartTimes() {
        for (seconds in listOf(1, 7, 8, 15, 16, 30)) {
            fun run(delayed: Boolean): SessionState {
                var now = 0L
                val game = VisualSession.withTypes(MonotonicClock { now }) { type, n -> generateSequence(Random(type.bit), n, type.cardinality) }
                game.start(3, modeMask = 7, intervalSeconds = seconds)
                val interval = seconds * 1000L
                if (!delayed) for (i in 1..23) { now = i * interval; game.advance() }
                else { now = 23 * interval; game.advance() }
                return game.state
            }
            assertEquals(run(false), run(true))
            assertTrue(run(true).results.values.all { it.accuracy == 70 })
        }
        val mixed = listOf(Outcome.HIT, Outcome.MISS, Outcome.CORRECT_REJECTION, Outcome.FALSE_ALARM) + List(16) { Outcome.HIT }
        val points = accuracyTimeline(SessionConfig(intervalSeconds = 8), mixed)
        assertEquals(listOf(100.0, 50.0, 200.0/3, 50.0), points.take(4).map { it.percentage })
        assertEquals(listOf(24000L,32000L,40000L,48000L), points.take(4).map { it.elapsedMillis })
        assertEquals(mixed, decodeOutcomes(mixed.joinToString("") { it.storageCode().toString() }))
    }
    @Test fun practiceUsesExposureAndWaitsAtFeedbackUntilNext() {
        for (seconds in listOf(1,7,8,15,16,30)) {
            var now = 0L
            val game = VisualSession(MonotonicClock { now }) { n -> generateSequence(Random(0), n) }
            game.start(2, practice = true, intervalSeconds = seconds)
            now = 3 * seconds * 1000L; game.advance()
            assertEquals(SessionScreen.PRACTICE_FEEDBACK, game.state.screen)
            val feedback = game.state
            now += 100000; game.advance(); assertEquals(feedback, game.state)
            game.nextExample(feedback.feedback!!.token)
            assertEquals(SessionScreen.PLAYING, game.state.screen)
            assertEquals(game.state.config.exposureMillis, game.millisUntilNextChange())
            assertTrue(game.state.outcomes.isEmpty())
        }
    }
    @Test fun invalidIntervalsAndInvalidTimelineCodesAreRejected() {
        for (seconds in listOf(Int.MIN_VALUE, 0, 31, Int.MAX_VALUE)) {
            try { SessionConfig(intervalSeconds = seconds); fail("Invalid interval") } catch (_: IllegalArgumentException) { }
        }
        try { decodeOutcomes("HMX"); fail("Invalid code") } catch (_: IllegalArgumentException) { }
        try { accuracyTimeline(SessionConfig(), emptyList()); fail("Incomplete timeline") } catch (_: IllegalArgumentException) { }
    }
}
