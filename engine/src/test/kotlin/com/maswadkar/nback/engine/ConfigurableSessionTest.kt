package com.maswadkar.nback.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ConfigurableSessionTest {
    private var time = 0L
    private fun game() = VisualSession(MonotonicClock { time }) { n -> generateSequence(Random(42), n) }
    private fun at(game: VisualSession, t: Long) { time = t; game.advance() }

    @Test fun validatesLevelsAndGeneratesSixMatchesAtEveryLevel() {
        for (bad in listOf(-1, 0, 4, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { SessionConfig(bad) }
            assertThrows(IllegalArgumentException::class.java) { game().start(bad) }
        }
        for (n in 1..3) {
            var first = false; var last = false; var adjacent = false
            repeat(1_000) { seed ->
                val cells = generateSequence(Random(seed), n)
                assertEquals(cells, generateSequence(Random(seed), n))
                assertEquals(n + 20, cells.size); assertTrue(cells.all { it in 0..8 })
                val matches = (n until cells.size).filter { cells[it] == cells[it - n] }
                assertEquals(6, matches.size)
                first = first || n in matches; last = last || cells.lastIndex in matches
                adjacent = adjacent || matches.zipWithNext().any { (a, b) -> b == a + 1 }
            }
            assertTrue(first && last && adjacent)
        }
    }

    @Test fun normalScoringAndBoundariesAtEveryLevel() {
        for (n in 1..3) {
            for (mode in 0..2) {
                time = 0; val game = game(); game.start(n)
                val cells = generateSequence(Random(42), n)
                at(game, 999); assertNotNull(game.state.highlightedCell)
                at(game, 1_000); assertNull(game.state.highlightedCell)
                at(game, 1_001); assertNull(game.state.highlightedCell)
                at(game, n * 3_000L - 1); game.match(); assertFalse(game.state.responseRecorded)
                for (i in n until cells.size) {
                    at(game, i * 3_000L)
                    assertTrue(game.state.canRespond)
                    at(game, i * 3_000L + 1_001) // responses in the blank phase count
                    if (mode == 1 || mode == 2 && cells[i] == cells[i - n]) { game.match(); game.match() }
                }
                val end = (n + 20) * 3_000L
                at(game, end - 1); assertEquals(SessionScreen.PLAYING, game.state.screen)
                at(game, end); game.match()
                val result = game.state
                assertEquals(when (mode) { 0 -> SessionResult(0,6,0,14); 1 -> SessionResult(6,0,14,0); else -> SessionResult(6,0,0,14) }, result.result)
                at(game, end + 1); game.interrupt(); assertSame(result, game.state)
            }
        }
    }

    @Test fun normalBoundaryActivationAndInterruptionAtEveryLevel() {
        for (n in 1..3) {
            time = 0; val game = game(); game.start(n)
            time = n * 3_000L; game.match(); assertTrue(game.state.responseRecorded)
            time = (n + 1) * 3_000L; game.match()
            assertEquals(n + 2, game.state.trial); assertTrue(game.state.responseRecorded)
            at(game, (n + 20) * 3_000L - 1); game.interrupt()
            assertEquals(SessionScreen.INTERRUPTED, game.state.screen); assertEquals(n, game.state.config.level)
            val restartOrigin = time; game.start(n)
            at(game, restartOrigin + (n + 20) * 3_000L); game.interrupt()
            assertEquals(SessionScreen.RESULTS, game.state.screen)
        }
    }

    @Test fun exactPracticeScriptsAndAllFourExplanations() {
        val scripts = listOf(listOf(1,1,5,5,9), listOf(1,5,1,9,1,4), listOf(1,5,9,1,2,9,4))
        for (n in 1..3) for (responses in listOf(setOf(0,1), emptySet(), setOf(0,1,2,3))) {
            assertEquals(scripts[n - 1].map { it - 1 }, practiceSequence(n))
            time = 0; val game = game(); game.start(n, practice = true)
            at(game, n * 3_000L - 1); game.match(); assertFalse(game.state.responseRecorded)
            var start = n * 3_000L
            repeat(4) { step ->
                at(game, start); assertEquals(step + 1, game.state.progress)
                if (step in responses) game.match()
                at(game, start + 2_999); assertNull(game.state.feedback)
                at(game, start + 3_000)
                val feedback = game.state.feedback!!
                assertEquals(scripts[n-1][n+step]-1, feedback.currentCell)
                assertEquals(scripts[n-1][step]-1, feedback.referenceCell)
                assertEquals(classify(step % 2 == 0, step in responses), feedback.outcome)
                assertNull(game.state.result)
                val stable = game.state
                at(game, time + 100_000); game.match(); assertSame(stable, game.state)
                if (step < 3) {
                    assertEquals(SessionScreen.PRACTICE_FEEDBACK, game.state.screen)
                    start = time; game.nextExample(feedback.token)
                } else assertEquals(SessionScreen.PRACTICE_COMPLETE, game.state.screen)
            }
        }
    }

    @Test fun feedbackBarrierDeadlineAndTokensPreventSkippedTurnsAndCrossRunEvents() {
        for (n in 1..3) {
            time = 0; val game = game(); game.start(n, true)
            time = 999_999; game.match() // giant delay stops at first explanation
            assertEquals(1, game.state.progress)
            assertFalse(game.state.feedback!!.responded)
            val first = game.state.feedback!!.token
            game.nextExample(first); game.nextExample(first)
            assertEquals(2, game.state.progress); assertFalse(game.state.responseRecorded)
            at(game, time + 3_000)
            val second = game.state
            game.nextExample(first); assertSame(second, game.state)
            game.interrupt(); assertEquals(SessionScreen.INTERRUPTED, game.state.screen)
            game.start(n, true); at(game, time + (n + 1) * 3_000L)
            val restarted = game.state
            game.nextExample(first); game.nextExample(second.feedback!!.token)
            assertSame(restarted, game.state)
        }
    }

    @Test fun practiceCompletionWinsAtFinalDeadlineAndOtherPhasesInterrupt() {
        for (n in 1..3) {
            time = 0; val game = game(); game.start(n, true)
            game.interrupt(); assertEquals(SessionScreen.INTERRUPTED, game.state.screen)
            game.start(n, true); at(game, (n + 1) * 3_000L)
            repeat(3) { game.nextExample(game.state.feedback!!.token); at(game, time + 3_000) }
            game.interrupt(); assertEquals(SessionScreen.PRACTICE_COMPLETE, game.state.screen)
            game.start(n); assertFalse(game.state.config.practice); assertNull(game.state.feedback)
            assertEquals(1, game.state.trial); game.home(); assertEquals(SessionState(), game.state)
            game.start(n, true); at(game, time + (n + 1) * 3_000L)
            game.interrupt(); assertEquals(SessionScreen.INTERRUPTED, game.state.screen)
        }
    }
}
