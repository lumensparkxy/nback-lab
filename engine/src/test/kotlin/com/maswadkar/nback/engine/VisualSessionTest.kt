package com.maswadkar.nback.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VisualSessionTest {
    private val example = listOf(1, 5, 1, 9, 2, 9, 4, 6, 4, 8, 3, 7, 3, 2, 5, 2, 8, 6, 9, 6, 1, 4).map { it - 1 }
    private class Clock(var time: Long = 0) : MonotonicClock {
        override fun nowMillis() = time
    }
    private val clock = Clock()
    private val session = VisualSession(clock) { example }
    private fun at(time: Long) { clock.time = time; session.advance() }

    @Test fun workedExampleHasAllFourExpectedOutcomes() {
        session.start()
        for (trial in listOf(3, 4, 6, 8, 9, 13, 15)) {
            at((trial - 1) * 3_000L + 500)
            session.match()
        }
        at(66_000)
        assertEquals(SessionResult(4, 2, 3, 11), session.state.result)
        assertEquals(75, session.state.result!!.accuracy)
        assertEquals(15, session.state.result!!.correct)
    }

    @Test fun exactStimulusWarmupAndResponseBoundaries() {
        session.match(); assertEquals(SessionScreen.HOME, session.state.screen)
        session.start()
        at(999); assertEquals(0, session.state.highlightedCell)
        at(1_000); assertNull(session.state.highlightedCell)
        at(1_001); assertNull(session.state.highlightedCell)
        at(5_999); session.match(); assertFalse(session.state.responseRecorded)
        at(6_000); assertTrue(session.state.canRespond)
        session.match(); assertTrue(session.state.responseRecorded)
        at(6_001); session.match(); assertFalse(session.state.canRespond)
        at(8_999); session.match(); assertEquals(3, session.state.trial)
        // Input, rather than a timer callback, advances into the new trial.
        clock.time = 9_000; session.match()
        assertEquals(4, session.state.trial)
        assertTrue(session.state.responseRecorded)
        at(9_001); session.match()
        at(66_000)
        assertEquals(SessionResult(1, 5, 1, 13), session.state.result)
    }

    @Test fun acceptsInputAfterHighlightDisappears() {
        session.start(); at(7_000)
        assertNull(session.state.highlightedCell)
        session.match(); at(66_000)
        assertEquals(1, session.state.result!!.hits)
    }

    @Test fun lateInputCannotModifyCompletedResultAndCompletionIsStable() {
        session.start(); at(65_999); session.match()
        clock.time = 66_000; session.match()
        val completed = session.state
        at(66_001); session.match(); session.interrupt(); at(100_000)
        assertSame(completed, session.state)
        assertEquals(SessionResult(0, 6, 1, 13), completed.result)
    }

    @Test fun noInputAllMatchAndPerfectSessions() {
        for (mode in listOf("none", "all", "perfect")) {
            clock.time = 0; session.home(); session.start()
            for (i in 2 until 22) {
                at(i * 3_000L)
                if (mode == "all" || (mode == "perfect" && example[i] == example[i - 2])) session.match()
            }
            at(66_000)
            assertEquals(when (mode) {
                "none" -> SessionResult(0, 6, 0, 14)
                "all" -> SessionResult(6, 0, 14, 0)
                else -> SessionResult(6, 0, 0, 14)
            }, session.state.result)
        }
    }

    @Test fun delayedCallbacksSkipExpiredStimuliWithoutExtendingSession() {
        session.start(); at(8_100)
        assertEquals(3, session.state.trial); assertNull(session.state.highlightedCell)
        assertEquals(900L, session.millisUntilNextChange())
        at(60_000); assertEquals(21, session.state.trial)
        assertEquals(1_000L, session.millisUntilNextChange())
        at(80_000); assertEquals(SessionResult(0, 6, 0, 14), session.state.result)
        assertNull(session.millisUntilNextChange())
    }

    @Test fun interruptionDeadlineOrderingAndIgnoredInput() {
        session.start(); at(65_999); session.interrupt()
        assertEquals(SessionScreen.INTERRUPTED, session.state.screen)
        at(66_000); session.match(); assertNull(session.state.result)
        session.start(); at(132_000); session.interrupt()
        assertEquals(SessionScreen.RESULTS, session.state.screen)
    }

    @Test fun restartAndHomeDiscardStateAndUseFreshSequence() {
        var generated = 0
        val game = VisualSession(clock) { generated++; generateSequence(Random(generated)) }
        game.start(); game.start(); assertEquals(1, generated)
        clock.time = 6_000; game.match(); game.interrupt(); game.start()
        assertEquals(2, generated); assertEquals(1, game.state.trial)
        assertFalse(game.state.responseRecorded); assertNull(game.state.result)
        game.home(); assertEquals(SessionState(), game.state)
        game.start(); assertEquals(3, generated)
        clock.time += 66_000; game.advance(); game.start()
        assertEquals(4, generated); assertEquals(1, game.state.trial)
    }

    @Test fun elapsedTimeOriginIsNotWallClockAndRepeatedReadsDoNotAdvance() {
        clock.time = 9_000_000; session.start()
        repeat(10) { assertEquals(1, session.state.trial); session.advance() }
        at(9_006_000); assertEquals(3, session.state.trial)
        // Defensive handling of an invalid backwards clock must never replay a trial.
        at(0); assertEquals(3, session.state.trial)
    }

    @Test fun seededGenerationHasExactlySixMatchesAndIncludesBoundaryPatterns() {
        var first = false; var last = false; var adjacent = false
        repeat(1_000) { seed ->
            val cells = generateSequence(Random(seed))
            assertEquals(cells, generateSequence(Random(seed)))
            assertEquals(22, cells.size); assertTrue(cells.all { it in 0..8 })
            val matches = (2..21).filter { cells[it] == cells[it - 2] }
            assertEquals(6, matches.size)
            first = first || 2 in matches; last = last || 21 in matches
            adjacent = adjacent || matches.zipWithNext().any { (a, b) -> b == a + 1 }
        }
        assertTrue(first && last && adjacent)
    }

    @Test fun rejectsInvalidSequencesBeforePlaying() {
        for (bad in listOf(emptyList(), List(22) { 0 }, List(22) { 9 })) {
            val game = VisualSession(clock) { bad }
            assertThrows(IllegalArgumentException::class.java) { game.start() }
            assertEquals(SessionScreen.HOME, game.state.screen)
        }
    }
}
