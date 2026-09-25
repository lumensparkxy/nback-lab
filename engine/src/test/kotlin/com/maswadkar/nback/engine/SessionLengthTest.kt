package com.maswadkar.nback.engine

import kotlin.random.Random
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class SessionLengthTest {
    @Test fun everyLengthModeLevelAndPaceHasExactTargetsAndCorrectCompleteTimelines() {
        for (length in SessionRules.LENGTHS) for (n in 1..3) for (mask in 1..7)
            for (pace in listOf(1,3,7,8,15,16,30)) for (response in 0..2) {
                var time = 0L
                val config = SessionConfig(n, modeMask=mask, intervalSeconds=pace, sessionLength=length)
                val streams = config.types.associateWith { generateSequence(Random(it.bit + length + n), n, it.cardinality, length) }
                streams.forEach { (type, stream) ->
                    assertEquals(n+length, stream.size)
                    assertTrue(stream.all { it in 0 until type.cardinality })
                    assertEquals(length*3/10, (n until stream.size).count { stream[it] == stream[it-n] })
                }
                val game = VisualSession.withConfig(MonotonicClock { time }) { type, _ -> streams.getValue(type) }
                game.start(n, modeMask=mask, intervalSeconds=pace, sessionLength=length)
                for (turn in n until config.totalTrials) {
                    time = turn*config.intervalMillis
                    config.types.forEach { type ->
                        if (response == 1 || (response == 2 && streams.getValue(type)[turn] == streams.getValue(type)[turn-n])) {
                            game.match(type); game.match(type) // Duplicates never add outcomes.
                        }
                    }
                }
                time = config.durationMillis
                game.match(config.types.first()) // Final-deadline input cannot alter the last turn.
                assertEquals(SessionScreen.RESULTS, game.state.screen)
                assertEquals(config, game.state.config)
                game.state.results.forEach { (type,result) ->
                    assertEquals(length,result.total)
                    assertEquals(length*3/10,result.hits+result.misses)
                    assertEquals(length*7/10,result.falseAlarms+result.correctRejections)
                    assertEquals(listOf(70,30,100)[response],result.accuracy)
                    val timeline = accuracyTimeline(config,game.state.outcomes.getValue(type))
                    assertEquals(length,timeline.size)
                    assertEquals((n+1)*config.intervalMillis,timeline.first().elapsedMillis)
                    assertEquals(config.durationMillis,timeline.last().elapsedMillis)
                    assertEquals(result.accuracy,timeline.last().percentage.roundToInt())
                }
                val completed=game.state;time += 999999;game.advance();assertEquals(completed,game.state)
            }
    }
    @Test fun practiceRemainsFourExamplesAndKeepsChosenNormalLength() {
        for(length in SessionRules.LENGTHS) for(n in 1..3) for(mask in 1..7) {
            var time=0L
            val game=VisualSession.withConfig(MonotonicClock { time }) { _,_ -> error("Practice must use its script") }
            game.start(n,practice=true,modeMask=mask,intervalSeconds=1,sessionLength=length)
            time=(n+1)*1000L;game.advance()
            repeat(3) { step ->
                assertEquals(SessionScreen.PRACTICE_FEEDBACK,game.state.screen)
                assertEquals(step+1,game.state.progress)
                val token=game.state.feedback!!.token;time+=900000;game.advance()
                game.nextExample(token);time+=1000;game.advance()
            }
            assertEquals(SessionScreen.PRACTICE_COMPLETE,game.state.screen)
            assertEquals(4,game.state.progress);assertEquals(length,game.state.config.sessionLength)
        }
    }
    @Test fun invalidLengthRejectedAndFractionalPercentagesRoundConsistently() {
        for(length in listOf(-1,0,19,21,100,Int.MAX_VALUE)) {
            try { SessionConfig(sessionLength=length);fail("Invalid length accepted") } catch (_: IllegalArgumentException) { }
        }
        val result=SessionResult(6,3,7,14)
        assertEquals(30,result.total);assertEquals(67,result.accuracy)
        assertEquals(0,SessionResult().accuracy)
    }
}
