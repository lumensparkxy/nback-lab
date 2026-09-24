package com.maswadkar.nback

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.maswadkar.nback.engine.StimulusType
import com.maswadkar.nback.engine.MonotonicClock
import com.maswadkar.nback.engine.SessionScreen
import com.maswadkar.nback.engine.VisualSession
import com.maswadkar.nback.engine.generateSequence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class LevelSettingsTest {
    private val historyJob = SupervisorJob()
    private val historyScope = CoroutineScope(Dispatchers.Main.immediate + historyJob)
    @org.junit.After fun closeHistory() { historyJob.cancel() }
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 5_000
        while (!main(condition)) {
            check(android.os.SystemClock.elapsedRealtime() < end) { "Settings state timed out" }
            Thread.sleep(10)
        }
    }
    private class PendingSettings : LevelSettings {
        val load = CompletableDeferred<LoadedLevel>()
        data class Write(val level: Int, val modeMask: Int, val intervalSeconds: Int, val done: CompletableDeferred<Unit> = CompletableDeferred())
        val writes = Channel<Write>(Channel.UNLIMITED)
        var stored: Int? = null
        var storedMask: Int? = null
        override suspend fun load() = load.await()
        override suspend fun save(level: Int, modeMask: Int, intervalSeconds: Int) {
            val write = Write(level, modeMask, intervalSeconds); writes.send(write); write.done.await(); stored = level; storedMask = modeMask
        }
        suspend fun next(): Write = withTimeout(5_000) { writes.receive() }
    }

    @Test fun loadingRacesFailuresRetryAndActiveSessionIsolation() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore()
        val model = main { SessionViewModel(prefs, testHistory(historyScope)).also { holder.put("test", it); it.resume() } }
        try {
            main { model.selectLevel(1); model.start(); model.practice() }
            assertTrue(main { model.settings.loading }); assertEquals(SessionScreen.HOME, main { model.state.screen })
            prefs.load.complete(LoadedLevel(3)); await { !model.settings.loading }
            assertEquals(3, main { model.settings.level })
            main { model.selectLevel(1) }
            val first = prefs.next()
            main { model.selectLevel(2); model.selectLevel(3); model.start() }
            assertEquals(3, main { model.state.config.level }); assertTrue(main { model.settings.saving })
            first.done.completeExceptionally(IOException("older failed write"))
            val second = prefs.next(); assertEquals(2, second.level)
            assertNull(main { model.settings.notice }); assertTrue(main { model.settings.saving })
            second.done.complete(Unit)
            val latest = prefs.next(); assertEquals(3, latest.level)
            assertTrue(main { model.settings.saving })
            latest.done.completeExceptionally(IOException("disk unavailable"))
            await { model.settings.notice == SettingsNotice.SAVE_FAILED }
            assertFalse(main { model.settings.saving }); assertEquals(3, main { model.state.config.level })
            main { model.selectLevel(1) }; assertEquals(3, main { model.settings.level })
            main { model.home(); model.retrySave() }
            val retry = prefs.next(); assertEquals(3, retry.level)
            main { model.selectLevel(1) }
            retry.done.complete(Unit)
            val final = prefs.next(); assertEquals(1, final.level)
            assertTrue(main { model.settings.saving }); final.done.complete(Unit)
            await { !model.settings.saving }
            assertEquals(1, prefs.stored); assertNull(main { model.settings.notice })
        } finally { main { holder.clear() } }
    }

    @Test fun intervalWritesStayOrderedAndRetryKeepsTheFullConfiguration() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore()
        val model = main { SessionViewModel(prefs, testHistory(historyScope)).also { holder.put("interval", it) } }
        try {
            prefs.load.complete(LoadedLevel(3, modeMask = 7, intervalSeconds = 8)); await { !model.settings.loading }
            main { model.selectInterval(15) }; val older = prefs.next()
            main { model.selectInterval(16); model.start() }
            assertEquals(16, main { model.state.config.intervalSeconds })
            older.done.completeExceptionally(IOException("older write"))
            val current = prefs.next(); assertEquals(16, current.intervalSeconds)
            assertEquals(3, current.level); assertEquals(7, current.modeMask)
            assertNull(main { model.settings.notice })
            current.done.completeExceptionally(IOException("latest write"))
            await { model.settings.notice == SettingsNotice.SAVE_FAILED }
            main { model.selectInterval(30) }; assertEquals(16, main { model.settings.intervalSeconds })
            main { model.home(); model.retrySave() }
            val retry = prefs.next(); assertEquals(16, retry.intervalSeconds)
            assertEquals(3, retry.level); assertEquals(7, retry.modeMask)
            retry.done.complete(Unit); await { !model.settings.saving }
            assertNull(main { model.settings.notice })
        } finally { main { holder.clear() } }
    }

    @Test fun invalidRepairCannotOverwriteNewSelectionAndReadFailureStillAllowsPlay() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore()
        val model = main { SessionViewModel(prefs, testHistory(historyScope)).also { holder.put("test", it) } }
        try {
            prefs.load.complete(LoadedLevel(99)); await { !model.settings.loading }
            assertEquals(2, main { model.settings.level }); assertEquals(SettingsNotice.RESET, main { model.settings.notice })
            val repair = prefs.next(); assertEquals(2, repair.level)
            main { model.selectLevel(3) }; repair.done.complete(Unit)
            val selected = prefs.next(); assertEquals(3, selected.level)
            selected.done.complete(Unit); await { !model.settings.saving }; assertEquals(3, prefs.stored)
        } finally { main { holder.clear() } }
        val failed = PendingSettings(); val failedHolder = ViewModelStore()
        val fallback = main { SessionViewModel(failed, testHistory(historyScope)).also { failedHolder.put("test", it) } }
        try {
            failed.load.completeExceptionally(IOException("read unavailable")); await { !fallback.settings.loading }
            assertEquals(SettingsNotice.LOAD_FAILED, main { fallback.settings.notice })
            main { fallback.practice() }; assertEquals(2, main { fallback.state.config.level })
            assertTrue(main { fallback.state.config.practice })
        } finally { main { failedHolder.clear() } }
    }

    @Test fun practiceToNormalAndRestartKeepSnapshotWhileStorageFinishes() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore(); var time = 0L
        val engine = VisualSession(MonotonicClock { time }) { n -> generateSequence(Random(1), n) }
        val model = main { SessionViewModel(prefs, testHistory(historyScope), engine).also { holder.put("test", it); it.resume() } }
        try {
            prefs.load.complete(LoadedLevel()); await { !model.settings.loading }
            main { model.selectLevel(3); model.practice() }
            val write = prefs.next(); write.done.complete(Unit); await { !model.settings.saving }
            main {
                time = 12_000; model.refresh()
                repeat(3) { model.nextExample(model.state.feedback!!.token); time += 3_000; model.refresh() }
                assertEquals(SessionScreen.PRACTICE_COMPLETE, model.state.screen)
                model.start(); assertEquals(3, model.state.config.level); assertFalse(model.state.config.practice)
                model.interrupt(); model.start(); assertEquals(3, model.state.config.level)
                model.home(); assertEquals(3, model.settings.level)
            }
        } finally { main { holder.clear() } }
    }

    @Test fun modeAndLevelWritesAreOrderedRetryableAndFrozenDuringSession() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore()
        val model = main { SessionViewModel(prefs, testHistory(historyScope)).also { holder.put("test", it) } }
        try {
            prefs.load.complete(LoadedLevel(3)); await { !model.settings.loading }
            main { model.toggleType(StimulusType.COLOUR) }
            val first = prefs.next(); assertEquals(3, first.level); assertEquals(3, first.modeMask)
            main { model.selectLevel(1); model.toggleType(StimulusType.NUMBER); model.start() }
            assertEquals(7, main { model.state.config.modeMask }); assertEquals(1, main { model.state.config.level })
            main { model.toggleType(StimulusType.POSITION) }; assertEquals(7, main { model.settings.modeMask })
            first.done.completeExceptionally(IOException("old failure"))
            val second = prefs.next(); assertEquals(1, second.level); assertEquals(3, second.modeMask)
            assertNull(main { model.settings.notice }); second.done.complete(Unit)
            val latest = prefs.next(); assertEquals(1, latest.level); assertEquals(7, latest.modeMask)
            latest.done.completeExceptionally(IOException("latest failure")); await { model.settings.notice == SettingsNotice.SAVE_FAILED }
            main { model.interrupt(); model.start() }
            assertEquals(7, main { model.state.config.modeMask }); assertEquals(1, main { model.state.config.level })
            main { model.home(); model.retrySave() }
            val retry = prefs.next(); assertEquals(7, retry.modeMask)
            main { model.toggleType(StimulusType.POSITION) }; retry.done.complete(Unit)
            val final = prefs.next(); assertEquals(1, final.level); assertEquals(6, final.modeMask)
            assertTrue(main { model.settings.saving }); final.done.complete(Unit); await { !model.settings.saving }
            assertEquals(6, prefs.storedMask); assertEquals(1, prefs.stored)
        } finally { main { holder.clear() } }
    }

    @Test fun invalidModeRepairRetainsValidLevelAndCannotOverwriteNewTypes() = runBlocking {
        val prefs = PendingSettings(); val holder = ViewModelStore()
        val model = main { SessionViewModel(prefs, testHistory(historyScope)).also { holder.put("test", it) } }
        try {
            prefs.load.complete(LoadedLevel(3, modeMask = 99)); await { !model.settings.loading }
            assertEquals(SettingsNotice.TYPES_RESET, main { model.settings.notice })
            val repair = prefs.next(); assertEquals(3, repair.level); assertEquals(1, repair.modeMask)
            main { model.toggleType(StimulusType.COLOUR); model.practice() }
            assertEquals(3, main { model.state.config.modeMask }); assertEquals(3, main { model.state.config.level })
            repair.done.complete(Unit)
            val selection = prefs.next(); assertEquals(3, selection.modeMask)
            selection.done.complete(Unit); await { !model.settings.saving }; assertEquals(3, prefs.storedMask)
        } finally { main { holder.clear() } }
    }

    @CriticalCi @Test fun dataStoreRoundTripsMissingInvalidTypesRangesAndCorruption() = runBlocking {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val dir = File(cache, "settings-test-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "level.preferences_pb")
        var job = SupervisorJob()
        try {
            fun store() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            var data = store(); var adapter = StoredLevelSettings(data)
            assertEquals(LoadedLevel(), adapter.load())
            for (n in 1..3) { adapter.save(n); assertEquals(LoadedLevel(n), adapter.load()) }
            job.cancelAndJoin(); job = SupervisorJob(); data = store(); adapter = StoredLevelSettings(data)
            assertEquals(LoadedLevel(3), adapter.load()) // new instance, same file
            data.edit { it[intPreferencesKey("selected_n")] = 0 }
            assertEquals(LoadedLevel(2, true), adapter.load())
            data.edit { it[stringPreferencesKey("selected_n")] = "three" }
            assertEquals(LoadedLevel(2, true), adapter.load())
            adapter.save(2); assertEquals(LoadedLevel(), adapter.load())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { adapter.save(4) } }
            job.cancelAndJoin(); job = SupervisorJob(); file.writeBytes(byteArrayOf(0))
            val corrupted = AtomicBoolean(false)
            val recovered = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job),
                corruptionHandler = ReplaceFileCorruptionHandler { corrupted.set(true); emptyPreferences() }, produceFile = { file })
            val repaired = StoredLevelSettings(recovered) { corrupted.getAndSet(false) }
            assertEquals(LoadedLevel(2, true, typesReset = true, intervalReset = true), repaired.load())
            repaired.save(2); assertEquals(LoadedLevel(), repaired.load())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
