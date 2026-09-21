package com.example.nback

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomHistoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file() = File(context.cacheDir, "history-test-${System.nanoTime()}/history.db").also { it.parentFile!!.mkdirs() }
    private fun sql(file: File, vararg statements: String) {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            // Room's asynchronous invalidation work may briefly hold the writer lock.
            // Wait for it before injecting fixtures; timeout still fails the test.
            connection.prepare("PRAGMA busy_timeout = 5000").use { it.step() }
            statements.forEach { query -> connection.prepare(query).use { it.step() } }
        }
    }
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected storage failure") } catch (_: Exception) { /* expected */ }
    }
    @Test fun creationReopenOrderingIdenticalRetryConflictAndAtomicClear() = runBlocking {
        val file = file(); var store = RoomHistoryStore(context, file)
        try {
            assertTrue(store.load().isEmpty())
            val a = sampleRecord("a", 1); val b = sampleRecord("b", 3); val earlier = sampleRecord("earlier", 2, a.completedAt - 1)
            store.save(b); store.save(a); store.save(a); store.save(earlier)
            fails { store.save(a.copy(level = 2)) }
            assertEquals(listOf(a, b, earlier), store.load())
            store.close(); store = RoomHistoryStore(context, file)
            assertEquals(listOf(a, b, earlier), store.load())
            store.close()
            sql(file, "CREATE TRIGGER fail_delete BEFORE DELETE ON sessions WHEN OLD.id = 'b' BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
            fails { store.clear() }
            assertEquals(listOf(a, b, earlier), store.load())
            store.close(); sql(file, "DROP TRIGGER fail_delete")
            store.clear(); store.close(); assertTrue(store.load().isEmpty())
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
    @Test fun invalidRecordsCannotBeWrittenOrSilentlyDroppedOnRead() = runBlocking {
        val file = file(); val store = RoomHistoryStore(context, file)
        try {
            val good = sampleRecord()
            for (bad in listOf(good.copy(level = 0), good.copy(level = 4), good.copy(rulesVersion = 3),
                good.copy(hits = -1), good.copy(hits = Int.MAX_VALUE, misses = Int.MAX_VALUE),
                good.copy(falseAlarms = 4), good.copy(completedAt = Long.MAX_VALUE), good.copy(id = ""))) {
                fails { store.save(bad) }
            }
            assertTrue(store.load().isEmpty()); store.save(good); store.close()
            sql(file, "UPDATE sessions SET hits = 4294967300") // Int conversion would otherwise look like 4.
            val bytes = file.readBytes()
            fails { store.load() }; fails { store.save(good.copy(id = "new")) }; fails { store.clear() }
            assertArrayEquals(bytes, file.readBytes())
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
    @Test fun corruptTruncatedEmptyAndIncompatibleStoresArePreservedAcrossEveryRetry() = runBlocking {
        for (kind in listOf("random", "empty", "truncated", "version", "identity", "columns")) {
            val file = file(); val store = RoomHistoryStore(context, file)
            try {
                store.save(sampleRecord()); store.close()
                when (kind) {
                    "random" -> file.writeText("not a database")
                    "empty" -> file.writeBytes(byteArrayOf())
                    "truncated" -> file.writeBytes(file.readBytes().take(120).toByteArray())
                    "version" -> sql(file, "PRAGMA user_version = 3")
                    "identity" -> sql(file, "UPDATE room_master_table SET identity_hash = 'different'")
                    "columns" -> sql(file, "ALTER TABLE sessions ADD COLUMN unexpected INTEGER")
                }
                val bytes = file.readBytes()
                repeat(2) {
                    fails { store.load() }; fails { store.save(sampleRecord("new")) }; fails { store.clear() }
                    assertArrayEquals(kind, bytes, file.readBytes())
                }
            } finally { store.close(); file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun sqliteAffinityCannotHideFractionalTextOrBlobValues() = runBlocking {
        for (update in listOf("hits = 4.9, misses = 2.1", "hits = '4oops'", "completedAt = 1750000000000.5",
            "level = 2.5", "id = X'6162'", "hits = X'34'", "rulesVersion = 'one'")) {
            val file = file(); val store = RoomHistoryStore(context, file)
            try {
                store.save(sampleRecord()); store.close()
                sql(file, "UPDATE sessions SET $update")
                val bytes = file.readBytes()
                fails { store.load() }; fails { store.save(sampleRecord("new")) }; fails { store.clear() }
                assertArrayEquals(update, bytes, file.readBytes())
            } finally { store.close(); file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun alreadyOpenDatabaseValidatesRawTypesBeforeRoomMapsResults() = runBlocking {
        val file = file(); val store = RoomHistoryStore(context, file)
        try {
            store.save(sampleRecord())
            sql(file, "UPDATE sessions SET hits = 4.9, misses = 2.1")
            fails { store.load() }
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
    @Test fun committedWalIsReadableAndTenThousandRecordsHaveDeterministicOrder() = runBlocking {
        val file = file(); val store = RoomHistoryStore(context, file)
        try {
            store.load(); store.close()
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                connection.prepare("PRAGMA journal_mode = WAL").use { it.step() }
                connection.prepare("PRAGMA wal_autocheckpoint = 0").use { it.step() }
                connection.prepare("BEGIN TRANSACTION").use { it.step() }
                connection.prepare("INSERT INTO sessions (id, completedAt, level, rulesVersion, hits, misses, falseAlarms, correctRejections) VALUES (?, ?, ?, 1, 4, 2, 3, 11)").use { insert ->
                    repeat(10_000) { index ->
                        insert.bindText(1, "record-%05d".format(index)); insert.bindLong(2, 1_750_000_000_000L + index / 2)
                        insert.bindLong(3, (index % 3 + 1).toLong()); insert.step(); insert.reset()
                    }
                }
                connection.prepare("COMMIT").use { it.step() }
                assertTrue(File(file.path + "-wal").length() > 0)
                val rows = store.load() // preflight must honor live WAL, not immutable-mode stale bytes.
                assertEquals(10_000, rows.size)
                assertEquals("record-09998", rows.first().id)
                assertEquals("record-00001", rows.last().id)
                assertEquals(3334, rows.count { it.level == 1 })
            }
            store.close(); assertEquals(10_000, store.load().size)
        } finally { store.close(); file.parentFile!!.deleteRecursively() }
    }
}
