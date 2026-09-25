package com.maswadkar.nback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal fun multiRecord(id: String = "multi", mask: Int = 7): HistoryRecord = HistoryRecord(
    id, 1_750_000_000_000L, 2, rulesVersion = 2, modeMask = mask,
    hits = if (mask and 1 != 0) 4 else 0, misses = if (mask and 1 != 0) 2 else 0,
    falseAlarms = if (mask and 1 != 0) 3 else 0, correctRejections = if (mask and 1 != 0) 11 else 0,
    colourHits = 0, colourMisses = if (mask and 2 != 0) 6 else 0, colourFalseAlarms = 0, colourCorrectRejections = if (mask and 2 != 0) 14 else 0,
    numberHits = if (mask and 4 != 0) 6 else 0, numberMisses = 0, numberFalseAlarms = if (mask and 4 != 0) 14 else 0, numberCorrectRejections = 0,
)

class MultiTypeStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file() = File(context.cacheDir, "multi-history-${System.nanoTime()}/history.db").also { it.parentFile!!.mkdirs() }
    private fun sql(file: File, vararg queries: String) {
        BundledSQLiteDriver().open(file.absolutePath).use { db -> queries.forEach { query -> db.prepare(query).use { it.step() } } }
    }
    private fun legacy(file: File) {
        sql(file, "CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, completedAt INTEGER NOT NULL, level INTEGER NOT NULL, rulesVersion INTEGER NOT NULL, hits INTEGER NOT NULL, misses INTEGER NOT NULL, falseAlarms INTEGER NOT NULL, correctRejections INTEGER NOT NULL)",
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT INTO room_master_table VALUES(42, '${RoomHistoryStore.V1_IDENTITY}')", "PRAGMA user_version = 1",
            "INSERT INTO sessions VALUES('old-1',1750000000000,1,1,4,2,3,11)",
            "INSERT INTO sessions VALUES('old-3',1750000000001,3,1,0,6,0,14)")
    }
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected explicit storage failure") } catch (_: Exception) { }
    }
    @CriticalCi @Test fun realV1MigrationPreservesEveryFieldAndSupportsNewModesAfterReopen() = runBlocking {
        val file = file(); legacy(file); var store = RoomHistoryStore(context, file)
        try {
            val old = listOf(sampleRecord("old-3", 3, 1_750_000_000_001L).copy(hits = 0, misses = 6, falseAlarms = 0, correctRejections = 14), sampleRecord("old-1", 1))
            assertEquals(old, store.load())
            for (mask in 1..7) { val record = multiRecord("mask-$mask", mask); store.save(record); store.save(record) }
            store.close(); store = RoomHistoryStore(context, file)
            assertEquals(9, store.load().size)
            assertEquals(old.toSet(), store.load().filter { it.id.startsWith("old") }.toSet())
            for (mask in 1..7) assertEquals(multiRecord("mask-$mask", mask), store.load().single { it.id == "mask-$mask" })
            fails { store.save(multiRecord("mask-7").copy(colourHits = 1, colourMisses = 5)) }
            assertEquals(multiRecord("mask-7"), store.load().single { it.id == "mask-7" })
            store.clear(); store.close(); assertTrue(store.load().isEmpty())
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
    @Test fun invalidOldRowsAreRejectedBeforeMigrationAndRemainUnchanged() = runBlocking {
        for (update in listOf("hits = 4.5", "level = 'bad'", "rulesVersion = 2", "correctRejections = 12")) {
            val file = file(); legacy(file); val store = RoomHistoryStore(context, file)
            try {
                sql(file, "UPDATE sessions SET $update"); val before = file.readBytes()
                repeat(2) { fails { store.load() }; fails { store.save(multiRecord()) }; fails { store.clear() }; assertArrayEquals(before, file.readBytes()) }
            } finally { store.close(); file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun invalidModeAndPerTypeRawValuesCannotBeCoercedOrDiscarded() = runBlocking {
        for (update in listOf("modeMask = 0", "modeMask = 8", "modeMask = 1.5", "modeMask = 'seven'",
            "colourHits = 0.5, colourMisses = 5.5", "numberHits = 4294967302", "colourMisses = X'36'",
            "modeMask = 1", "rulesVersion = 1")) {
            val file = file(); val store = RoomHistoryStore(context, file)
            try {
                store.save(multiRecord()); store.close(); sql(file, "UPDATE sessions SET $update")
                val before = file.readBytes()
                fails { store.load() }; fails { store.save(multiRecord("new")) }; fails { store.clear() }
                assertArrayEquals(update, before, file.readBytes())
            } finally { store.close(); file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun settingsRetainOldLevelAndValidateEachFieldIndependently() = runBlocking {
        val dir = File(context.cacheDir, "multi-settings-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "settings.preferences_pb"); var job = SupervisorJob()
        try {
            fun store() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            var data = store(); var adapter = StoredLevelSettings(data)
            data.edit { it[intPreferencesKey("selected_n")] = 3 }
            assertEquals(LoadedLevel(3), adapter.load())
            for (mask in 1..7) { adapter.save(3, mask); assertEquals(LoadedLevel(3, modeMask = mask), adapter.load()) }
            job.cancelAndJoin(); job = SupervisorJob(); data = store(); adapter = StoredLevelSettings(data)
            assertEquals(LoadedLevel(3, modeMask = 7), adapter.load())
            for (bad in listOf(0, 8, -1)) {
                data.edit { it[intPreferencesKey("selected_types")] = bad }
                assertEquals(LoadedLevel(3, modeMask = 1, typesReset = true), adapter.load())
            }
            data.edit { it[stringPreferencesKey("selected_types")] = "all" }
            assertEquals(LoadedLevel(3, typesReset = true), adapter.load())
            adapter.save(3, 6); data.edit { it[intPreferencesKey("selected_n")] = 4 }
            assertEquals(LoadedLevel(2, reset = true, modeMask = 6), adapter.load())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }
}
