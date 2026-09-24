package com.maswadkar.nback.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MultitypeSessionTest {
    @Test fun everyModeAndLevelHasIndependentValidStreamsAndScores() {
        for (n in 1..3) for (mask in 1..7) for (responseMode in 0..3) {
            var time = 0L
            val streams = activeTypes(mask).associateWith { generateSequence(Random(it.bit * 100 + n), n, it.cardinality) }
            val game = VisualSession.withTypes(MonotonicClock { time }) { type, _ -> streams.getValue(type) }
            game.start(n, modeMask = mask)
            assertEquals(activeTypes(mask).toSet(), game.state.stimulus.keys)
            StimulusType.entries.forEach(game::match)
            assertTrue(game.state.recordedTypes.isEmpty())
            val expected = activeTypes(mask).associateWith { SessionResult() }.toMutableMap()
            for (trial in n until n + 20) {
                time = trial * 3000L + 1000; game.advance()
                assertTrue(game.state.stimulus.isEmpty())
                for (type in StimulusType.entries) {
                    if (type !in streams) { game.match(type); assertFalse(game.state.canRespond(type)); continue }
                    val target = streams.getValue(type)[trial] == streams.getValue(type)[trial - n]
                    val respond = when (responseMode) { 0 -> false; 1 -> true; 2 -> target; else -> (trial + type.bit) % 3 == 0 }
                    expected[type] = expected.getValue(type).record(classify(target, respond))
                    if (respond) { game.match(type); game.match(type); assertFalse(game.state.canRespond(type)) }
                }
            }
            time = (n + 20) * 3000L; game.match(activeTypes(mask).first())
            assertEquals(SessionScreen.RESULTS, game.state.screen)
            assertEquals(expected, game.state.results)
            expected.values.forEach { assertEquals(6, it.hits + it.misses); assertEquals(14, it.falseAlarms + it.correctRejections) }
            val completed = game.state
            time++; game.interrupt(); assertSame(completed, game.state)
        }
    }

    @Test fun seededGenerationHasNoExtraTargetsAndAllowsIndependentOverlap() {
        var differing = false; var overlap = false
        for (n in 1..3) repeat(100) { seed ->
            val targets = StimulusType.entries.map { type ->
                val values = generateSequence(Random(seed + type.bit * 1000), n, type.cardinality)
                assertTrue(values.all { it in 0 until type.cardinality })
                (n until n + 20).filter { values[it] == values[it - n] }.toSet().also { assertEquals(6, it.size) }
            }
            differing = differing || targets.distinct().size > 1
            overlap = overlap || targets[0].intersect(targets[1]).isNotEmpty()
        }
        assertTrue(differing); assertTrue(overlap)
    }

    @Test fun boundaryAndInterruptionApplyToEachTypeAndResetOnRestart() {
        var time = 0L
        val game = VisualSession.withTypes(MonotonicClock { time }) { type, n -> generateSequence(Random(type.bit), n, type.cardinality) }
        game.start(modeMask = 7)
        time = 5999; game.match(StimulusType.COLOUR); assertTrue(game.state.recordedTypes.isEmpty())
        time = 6000; game.match(StimulusType.COLOUR)
        assertTrue(game.state.canRespond(StimulusType.NUMBER)); assertTrue(game.state.canRespond(StimulusType.POSITION))
        time = 8999; game.match(StimulusType.NUMBER)
        time = 9000; game.match(StimulusType.POSITION)
        assertEquals(setOf(StimulusType.POSITION), game.state.recordedTypes)
        game.interrupt(); assertEquals(7, game.state.config.modeMask); assertTrue(game.state.results.isEmpty())
        game.start(modeMask = 7); assertTrue(game.state.recordedTypes.isEmpty()); assertEquals(1, game.state.trial)
        game.home(); assertEquals(SessionState(), game.state)
        for (bad in listOf(0, 8, -1)) assertThrows(IllegalArgumentException::class.java) { game.start(modeMask = bad) }
    }

    @Test fun practiceScriptsCoverEveryCombinationWithIndependentFeedbackAndBarriers() {
        for (n in 1..3) for (mask in 1..7) {
            var time = 0L
            val game = VisualSession.withTypes(MonotonicClock { time }) { _, _ -> error("Practice must not randomize") }
            game.start(n, practice = true, modeMask = mask)
            val scripts = practiceSequences(game.state.config)
            val matchesByStep = (0..3).map { step -> scripts.filterValues { it[n + step] == it[step] }.keys }
            if (activeTypes(mask).size > 1) {
                assertTrue(matchesByStep[0].isEmpty()); assertEquals(1, matchesByStep[1].size)
                assertEquals(activeTypes(mask).toSet(), matchesByStep[2])
                if (mask == 7) assertEquals(2, matchesByStep[3].size)
            }
            activeTypes(mask).forEach { type -> assertTrue(matchesByStep.any { type in it }); assertTrue(matchesByStep.any { type !in it }) }
            time = n * 3000L; game.advance()
            repeat(4) { step ->
                val responded = activeTypes(mask).filterIndexed { index, _ -> (step + index) % 2 == 0 }.toSet()
                responded.forEach(game::match)
                time += 3000; game.advance()
                val feedback = game.state.feedback!!
                feedback.types.forEach { (type, value) ->
                    assertEquals(type in matchesByStep[step], value.matches)
                    assertEquals(type in responded, value.responded)
                }
                val frozen = game.state
                time += 100_000; game.match(activeTypes(mask).first()); assertSame(frozen, game.state)
                assertTrue(game.state.results.isEmpty())
                if (step < 3) { game.nextExample(feedback.token); game.nextExample(feedback.token); assertEquals(step + 2, game.state.progress) }
            }
            assertEquals(SessionScreen.PRACTICE_COMPLETE, game.state.screen)
        }
    }
}
