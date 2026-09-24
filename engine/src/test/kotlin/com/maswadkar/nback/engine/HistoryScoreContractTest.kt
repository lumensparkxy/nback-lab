package com.maswadkar.nback.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class HistoryScoreContractTest {
    @Test fun allLevelsPreserveTheFourPublishedScoreExamples() {
        for (n in 1..3) {
            val sequence = if (n == 2) listOf(1, 5, 1, 9, 2, 9, 4, 6, 4, 8, 3, 7, 3, 2, 5, 2, 8, 6, 9, 6, 1, 4).map { it - 1 }
                else generateSequence(Random(93 + n), n)
            val matches = (n until sequence.size).filter { sequence[it] == sequence[it - n] }
            val nonMatches = (n until sequence.size).filter { it !in matches }
            val fixtures = listOf(
                emptyList<Int>() to SessionResult(0, 6, 0, 14),
                (n until sequence.size).toList() to SessionResult(6, 0, 14, 0),
                matches to SessionResult(6, 0, 0, 14),
                (if (n == 2) listOf(2, 3, 5, 7, 8, 12, 14) else matches.take(4) + nonMatches.take(3)) to SessionResult(4, 2, 3, 11),
            )
            for ((respond, expected) in fixtures) {
                var now = 0L
                val game = VisualSession(MonotonicClock { now }) { sequence }
                game.start(n)
                for (index in n until sequence.size) {
                    now = index * 3_000L + 500
                    game.advance()
                    if (index in respond) game.match()
                }
                now = (n + 20) * 3_000L; game.advance()
                assertEquals("n=$n", expected, game.state.result)
                assertEquals(listOf(70, 30, 100, 75)[fixtures.indexOf(respond to expected)], game.state.result!!.accuracy)
            }
        }
    }
}
