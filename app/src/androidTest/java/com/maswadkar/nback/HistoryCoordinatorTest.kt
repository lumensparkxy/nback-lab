package com.maswadkar.nback

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maswadkar.nback.engine.StimulusType
import com.maswadkar.nback.engine.SessionResult
import com.maswadkar.nback.engine.activeTypes
import com.maswadkar.nback.engine.MonotonicClock
import com.maswadkar.nback.engine.SessionScreen
import com.maswadkar.nback.engine.VisualSession
import com.maswadkar.nback.engine.generateSequence
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class HistoryCoordinatorTest {
    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }
    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (!main(condition)) delay(10)
    }
    private class ControlledStore : HistoryStore {
        val rows = mutableMapOf<String, HistoryRecord>()
        var saveGate: CompletableDeferred<Unit>? = null
        var clearGate: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        var failSave = false
        var failRead = false
        var failClear = false
        var loseAck = false
        var saves = 0
        var clears = 0
        override suspend fun load(): List<HistoryRecord> {
            readGate?.await()
            if (failRead) throw IOException()
            return rows.values.sortedWith(compareByDescending<HistoryRecord> { it.completedAt }.thenBy { it.id })
        }
        override suspend fun save(record: HistoryRecord) {
            saves++; saveGate?.await()
            if (failSave) throw IOException()
            check(rows[record.id]?.let { it == record } != false)
            rows[record.id] = record
            if (loseAck) throw IOException()
        }
        override suspend fun clear() {
            clears++; clearGate?.await()
            if (failClear) throw IOException()
            rows.clear()
        }
    }
    @Test fun delayedSaveNavigationIndependentRetryAndReadFailureAreHonest() = runBlocking<Unit> {
        val job = SupervisorJob(); val store = ControlledStore()
        val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
        try {
            await { history.state.load == HistoryLoad.READY }
            main { store.saveGate = CompletableDeferred(); history.capture(multiRecord("one")); history.capture(multiRecord("one")); history.retry() }
            assertEquals(1, store.saves)
            assertEquals(SaveStatus.PENDING, main { history.state.entries.getValue("one").status })
            assertTrue(main { history.state.records.isEmpty() })
            main { store.failSave = true; store.saveGate!!.complete(Unit) }
            await { history.state.failed == 1 }
            main { store.failSave = false; store.saveGate = CompletableDeferred(); history.retry(); history.retry() }
            assertEquals(2, store.saves)
            main { store.saveGate!!.complete(Unit) }
            await { history.state.entries["one"]?.status == SaveStatus.SAVED }
            assertEquals(1, main { history.state.records.size })
            main { store.failRead = true; history.reload() }
            await { history.state.load == HistoryLoad.FAILED }
            assertTrue(main { history.state.records.isEmpty() }); assertFalse(main { history.state.canClear })
            main { store.failRead = false; history.reload() }
            await { history.state.load == HistoryLoad.READY }
            assertEquals(multiRecord("one"), main { history.state.records.single() })
        } finally { job.cancelAndJoin() }
    }
    @Test fun clearCutoffOrdersPendingWritesAndNeverResurrectsOldHandles() = runBlocking<Unit> {
        val job = SupervisorJob(); val store = ControlledStore()
        val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
        try {
            await { history.state.load == HistoryLoad.READY }
            main {
                store.saveGate = CompletableDeferred()
                history.capture(multiRecord("old")); history.clear(); history.clear(); history.retry("old")
                history.capture(multiRecord("new")); store.saveGate!!.complete(Unit)
            }
            await { history.state.entries["new"]?.status == SaveStatus.SAVED }
            assertEquals(1, store.clears); assertEquals(listOf("new"), main { history.state.records.map { it.id } })
            assertEquals(SaveStatus.REMOVED, main { history.state.entries["old"]?.status })
            main { history.retry("old"); history.capture(multiRecord("old")) }
            assertEquals(2, store.saves)
        } finally { job.cancelAndJoin() }
    }
    @Test fun failedClearRetainsSnapshotsAndLostAcknowledgementIsIdempotent() = runBlocking<Unit> {
        val job = SupervisorJob(); val store = ControlledStore()
        val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
        try {
            await { history.state.load == HistoryLoad.READY }
            main { store.loseAck = true; history.capture(multiRecord("one")) }
            await { history.state.failed == 1 }
            assertEquals(1, store.rows.size)
            main { store.failClear = true; history.clear() }
            await { history.state.clearFailed }
            assertEquals(1, store.rows.size); assertEquals(1, main { history.state.failed })
            main { store.loseAck = false; history.retry() }
            await { history.state.failed == 0 && history.state.pending == 0 }
            assertEquals(1, store.rows.size)
            main { store.failSave = true; history.capture(multiRecord("failed")) }
            await { history.state.failed == 1 }
            main { store.failClear = false; history.clear() }
            await { !history.state.clearing }
            assertTrue(store.rows.isEmpty()); assertEquals(0, main { history.state.failed })
            main { history.retry(); history.retry("failed") }
            assertEquals(3, store.saves)
        } finally { job.cancelAndJoin() }
    }
    @Test fun committedClearHidesDeletedRecordsBeforeSlowFollowupReadCompletes() = runBlocking<Unit> {
        val job = SupervisorJob(); val store = ControlledStore()
        val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
        try {
            await { history.state.load == HistoryLoad.READY }
            main { history.capture(multiRecord("one")) }
            await { history.state.records.size == 1 }
            main { store.readGate = CompletableDeferred(); history.clear() }
            await { history.state.entries["one"]?.status == SaveStatus.REMOVED }
            assertTrue(main { history.state.records.isEmpty() })
            assertFalse(main { history.state.canClear })
            main { store.readGate!!.complete(Unit) }
        } finally { job.cancelAndJoin() }
    }
    @Test fun cancellationNeverBecomesSavedOrCleared() = runBlocking<Unit> {
        val job = SupervisorJob(); val store = ControlledStore()
        val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
        await { history.state.load == HistoryLoad.READY }
        main { store.saveGate = CompletableDeferred(); history.capture(multiRecord("one")); history.clear() }
        job.cancelAndJoin()
        assertEquals(SaveStatus.PENDING, main { history.state.entries["one"]?.status })
        assertTrue(main { history.state.clearing }); assertTrue(store.rows.isEmpty())
    }
    @Test fun everyTerminalEventCapturesOnceAtEveryLevelAndViewModelClearDoesNotCancelSave() = runBlocking<Unit> {
        for (level in 1..3) for (event in listOf("refresh", "match", "back", "interrupt", "home")) {
            val job = SupervisorJob(); val store = ControlledStore(); var time = 0L; var ids = 0; var wallCalls = 0
            val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
            val holder = ViewModelStore()
            val interval = listOf(1, 8, 16)[level - 1]
            val preferences = object : LevelSettings {
                override suspend fun load() = LoadedLevel(level, intervalSeconds = interval)
                override suspend fun save(level: Int, modeMask: Int, intervalSeconds: Int, sessionLength: Int) = Unit
            }
            val engine = VisualSession(MonotonicClock { time }) { n -> generateSequence(Random(42), n) }
            val model = main { SessionViewModel(preferences, history, engine, { wallCalls++; 1_750_000_000_000L }, { "run-${++ids}" })
                .also { holder.put("model", it); it.resume() } }
            try {
                await { !model.settings.loading && history.state.load == HistoryLoad.READY }
                main {
                    store.saveGate = CompletableDeferred()
                    model.start(); model.start(); assertEquals(1, ids)
                    time = (level + 20) * interval * 1_000L
                    when (event) {
                        "match" -> model.match()
                        "back" -> model.back()
                        "interrupt" -> model.interrupt()
                        "home" -> model.home()
                        else -> model.refresh()
                    }
                    repeat(3) { model.refresh() }
                    assertEquals(1, wallCalls)
                    model.home(); holder.clear()
                }
                assertEquals(1, store.saves)
                main { store.saveGate!!.complete(Unit) }
                await { history.state.pending == 0 }
                val saved = store.rows.values.single()
                assertEquals(level, saved.level); assertEquals(70, saved.result().accuracy)
                assertEquals(interval, saved.intervalSeconds); assertEquals(4, saved.rulesVersion)
                assertEquals(20, saved.outcomes().getValue(StimulusType.POSITION).size)
            } finally { main { holder.clear() }; job.cancelAndJoin() }
        }
    }
    @Test fun viewModelCapturesEveryModeWithDistinctPerTypeScoresAtAllTerminalEvents() = runBlocking<Unit> {
        for (mask in 1..7) for (event in listOf("refresh", "match", "back", "interrupt", "home")) {
            val job = SupervisorJob(); val store = ControlledStore(); var time = 0L
            val history = main { HistoryCoordinator(store, CoroutineScope(job + Dispatchers.Main.immediate)) }
            val holder = ViewModelStore()
            val preferences = object : LevelSettings {
                override suspend fun load() = LoadedLevel(2, modeMask = mask)
                override suspend fun save(level: Int, modeMask: Int, intervalSeconds: Int, sessionLength: Int) = Unit
            }
            val streams = activeTypes(mask).associateWith { generateSequence(Random(it.bit), 2, it.cardinality) }
            val engine = VisualSession.withTypes(MonotonicClock { time }) { type, _ -> streams.getValue(type) }
            val model = main { SessionViewModel(preferences, history, engine, { 1_750_000_000_000L }, { "captured" })
                .also { holder.put("model", it); it.resume() } }
            try {
                await { !model.settings.loading && history.state.load == HistoryLoad.READY }
                main {
                    model.start()
                    for (i in 2 until 22) {
                        time = i * 3000L; model.refresh()
                        for (type in activeTypes(mask)) {
                            if (type == StimulusType.NUMBER || type == StimulusType.POSITION && streams.getValue(type)[i] == streams.getValue(type)[i - 2]) model.match(type)
                        }
                    }
                    time = 66000
                    when (event) {
                        "match" -> model.match(activeTypes(mask).first())
                        "back" -> model.back()
                        "interrupt" -> model.interrupt()
                        "home" -> model.home()
                        else -> model.refresh()
                    }
                    repeat(3) { model.refresh() }
                }
                await { history.state.records.size == 1 }
                val record = main { history.state.records.single() }
                assertEquals(mask, record.modeMask); assertEquals(4, record.rulesVersion); assertEquals(1, store.saves)
                val expected = activeTypes(mask).associateWith { type -> when (type) {
                    StimulusType.POSITION -> SessionResult(6, 0, 0, 14)
                    StimulusType.COLOUR -> SessionResult(0, 6, 0, 14)
                    StimulusType.NUMBER -> SessionResult(6, 0, 14, 0)
                } }
                assertEquals(expected, record.results()); record.validated()
                if (mask and 1 == 0) assertEquals(SessionResult(), record.result())
                if (mask and 2 == 0) assertEquals(listOf(0,0,0,0), listOf(record.colourHits, record.colourMisses, record.colourFalseAlarms, record.colourCorrectRejections))
                if (mask and 4 == 0) assertEquals(listOf(0,0,0,0), listOf(record.numberHits, record.numberMisses, record.numberFalseAlarms, record.numberCorrectRejections))
            } finally { main { holder.clear() }; job.cancelAndJoin() }
        }
    }

    @Test fun restartAndPlayAgainUseNewIdsPracticeAndPartialSessionsNeverSave() = runBlocking<Unit> {
        val job = SupervisorJob(); val history = main { testHistory(CoroutineScope(job + Dispatchers.Main.immediate)) }
        val holder = ViewModelStore(); var time = 0L; var ids = 0
        val preferences = object : LevelSettings {
            override suspend fun load() = LoadedLevel(1)
            override suspend fun save(level: Int, modeMask: Int, intervalSeconds: Int, sessionLength: Int) = Unit
        }
        val engine = VisualSession(MonotonicClock { time }) { n -> generateSequence(Random(1), n) }
        val model = main { SessionViewModel(preferences, history, engine, { 1_750_000_000_000L }, { "id-${++ids}" }).also { holder.put("m", it); it.resume() } }
        try {
            await { !model.settings.loading }
            main {
                model.practice(); time = 6_000; model.refresh()
                repeat(3) { model.nextExample(model.state.feedback!!.token); time += 3_000; model.refresh() }
                assertEquals(SessionScreen.PRACTICE_COMPLETE, model.state.screen); assertEquals(0, ids)
                model.start(); model.interrupt(); model.start(); assertEquals(2, ids)
                time += 63_000; model.refresh(); model.start(); assertEquals(3, ids)
                time += 63_000; model.refresh(); model.openHistory(); model.back()
                assertEquals(SessionScreen.RESULTS, model.state.screen)
            }
            await { history.state.records.size == 2 }
            assertEquals(listOf("id-2", "id-3"), main { history.state.records.map { it.id } })
        } finally { main { holder.clear() }; job.cancelAndJoin() }
    }
}
