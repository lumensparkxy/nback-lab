package com.maswadkar.nback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal fun timelineRecord(id: String = "timeline", mask: Int = 7, interval: Int = 8) = multiRecord(id, mask).copy(
    rulesVersion = 3, intervalSeconds = interval,
    positionOutcomes = if (mask and 1 != 0) "HHHHMMFFFCCCCCCCCCCC" else "",
    colourOutcomes = if (mask and 2 != 0) "MMMMMMCCCCCCCCCCCCCC" else "",
    numberOutcomes = if (mask and 4 != 0) "HHHHHHFFFFFFFFFFFFFF" else "",
)
class IntervalStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file() = File(context.cacheDir, "pace-${System.nanoTime()}/history.db").also { it.parentFile!!.mkdirs() }
    private fun sql(file: File, vararg statements: String) {
        BundledSQLiteDriver().open(file.absolutePath).use { db ->
            db.prepare("PRAGMA busy_timeout=5000").use { it.step() }
            statements.forEach { query -> db.prepare(query).use { it.step() } }
        }
    }
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: Exception) { }
    }
    @Test fun v2MigrationPreservesOriginalFieldsAndNewTimelinesRoundTrip() = runBlocking {
        val file = file(); val store = RoomHistoryStore(context, file)
        try {
            sql(file, "CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, completedAt INTEGER NOT NULL, level INTEGER NOT NULL, rulesVersion INTEGER NOT NULL, hits INTEGER NOT NULL, misses INTEGER NOT NULL, falseAlarms INTEGER NOT NULL, correctRejections INTEGER NOT NULL)",
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT INTO room_master_table VALUES(42,'${RoomHistoryStore.V2_IDENTITY}')", "PRAGMA user_version=2")
            BundledSQLiteDriver().open(file.absolutePath).use { RoomHistoryStore.MIGRATION_1_2.migrate(it) }
            sql(file, "INSERT INTO sessions (id,completedAt,level,rulesVersion,hits,misses,falseAlarms,correctRejections) VALUES ('old',1750000000000,2,2,4,2,3,11)")
            val old = multiRecord("old", 1)
            assertEquals(listOf(old), store.load()); assertTrue(store.load().first().outcomes().isEmpty())
            for (mask in 1..7) for (interval in listOf(1,7,8,15,16,30)) {
                val record = timelineRecord("$mask-$interval", mask, interval)
                store.save(record); store.save(record)
            }
            store.close()
            val all = store.load(); assertEquals(43, all.size)
            for (record in all.filter { it.rulesVersion == 3 }) {
                assertEquals(timelineRecord(record.id, record.modeMask, record.intervalSeconds), record)
                assertTrue(record.outcomes().values.all { it.size == 20 })
            }
            fails { store.save(timelineRecord("7-8").copy(positionOutcomes = "MHHHHCFFFCCCCCCCCCCM")) }
            // Reordering with identical histogram must also conflict with the saved ID.
            fails { store.save(timelineRecord("7-8").copy(positionOutcomes = timelineRecord().positionOutcomes.reversed())) }
            store.clear(); assertTrue(store.load().isEmpty())
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
    @Test fun malformedTimelinesAreRejectedBeforeLoadSaveAndClearEvenWhenOpen() = runBlocking {
        val changes = listOf("intervalSeconds=0", "intervalSeconds=31", "intervalSeconds=1.5", "intervalSeconds='slow'",
            "positionOutcomes='HHHHMMFFFCCCCCCCCCC'", "positionOutcomes='HHHHMMFFFCCCCCCCCCCCX'",
            "positionOutcomes='HHHHMMFFFCCCCCCCCCCX'", "positionOutcomes=positionOutcomes || char(0)",
            "positionOutcomes=CAST(positionOutcomes AS BLOB)", "positionOutcomes='HHHHHHFFFFFFFFFFFFFF'",
            "rulesVersion=2", "modeMask=6,hits=0,misses=0,falseAlarms=0,correctRejections=0")
        for (change in changes) for (operation in listOf("load", "save", "clear")) {
            val file = file(); val store = RoomHistoryStore(context, file)
            try {
                store.save(timelineRecord()); sql(file, "UPDATE sessions SET $change")
                fails { when (operation) { "load" -> store.load(); "save" -> store.save(timelineRecord("new")); else -> store.clear() } }
                store.close(); val before = file.readBytes()
                fails { store.load() }; fails { store.save(timelineRecord("new")) }; fails { store.clear() }
                assertArrayEquals(change, before, file.readBytes())
            } finally { store.close(); file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun intervalPreferencesRoundTripAndInvalidFieldDoesNotResetLevelOrTypes() = runBlocking {
        val file = File(context.cacheDir, "pace-settings-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            val store = StoredLevelSettings(data)
            assertEquals(3, store.load().intervalSeconds)
            for (seconds in 1..30) { store.save(3,7,seconds); assertEquals(LoadedLevel(3, modeMask = 7, intervalSeconds = seconds), store.load()) }
            for (bad in listOf(0,31,-1)) {
                data.edit { it[intPreferencesKey("interval_seconds")] = bad }
                assertEquals(LoadedLevel(3, modeMask = 7, intervalReset = true), store.load())
            }
            data.edit { it[stringPreferencesKey("interval_seconds")] = "fast" }
            assertEquals(LoadedLevel(3, modeMask = 7, intervalReset = true), store.load())
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
