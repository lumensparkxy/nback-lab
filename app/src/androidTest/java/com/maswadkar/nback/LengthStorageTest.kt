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

internal fun lengthRecord(id: String, length: Int = 10, mask: Int = 7) : HistoryRecord {
    val targets=length*3/10; val waits=length-targets
    return HistoryRecord(id,1750000000000,2,rulesVersion=4,sessionLength=length,modeMask=mask,
        hits=0,misses=if(mask and 1 != 0) targets else 0,falseAlarms=0,correctRejections=if(mask and 1 != 0) waits else 0,
        colourMisses=if(mask and 2 != 0) targets else 0,colourCorrectRejections=if(mask and 2 != 0) waits else 0,
        numberMisses=if(mask and 4 != 0) targets else 0,numberCorrectRejections=if(mask and 4 != 0) waits else 0,
        positionOutcomes=if(mask and 1 != 0) "M".repeat(targets)+"C".repeat(waits) else "",
        colourOutcomes=if(mask and 2 != 0) "M".repeat(targets)+"C".repeat(waits) else "",
        numberOutcomes=if(mask and 4 != 0) "M".repeat(targets)+"C".repeat(waits) else "")
}
class LengthStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file()=File(context.cacheDir,"length-${System.nanoTime()}/history.db").also { it.parentFile!!.mkdirs() }
    private fun sql(file:File,vararg statements:String) { BundledSQLiteDriver().open(file.absolutePath).use { db ->
        db.prepare("PRAGMA busy_timeout=5000").use { it.step() }
        statements.forEach { q -> db.prepare(q).use { it.step() } }
    } }
    private suspend fun rejects(block:suspend ()->Unit) { try { block();fail("Invalid history accepted") } catch (_:Exception) {} }
    @Test fun v3MigrationPreservesAllLegacyRulesAndNewLengthsRoundTrip()=runBlocking {
        val file=file();val store=RoomHistoryStore(context,file)
        try {
            sql(file,"CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, completedAt INTEGER NOT NULL, level INTEGER NOT NULL, rulesVersion INTEGER NOT NULL, hits INTEGER NOT NULL, misses INTEGER NOT NULL, falseAlarms INTEGER NOT NULL, correctRejections INTEGER NOT NULL)",
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)","INSERT INTO room_master_table VALUES(42,'${RoomHistoryStore.V3_IDENTITY}')","PRAGMA user_version=3")
            BundledSQLiteDriver().open(file.absolutePath).use { db -> RoomHistoryStore.MIGRATION_1_2.migrate(db);RoomHistoryStore.MIGRATION_2_3.migrate(db) }
            for(rule in 1..3) sql(file,"INSERT INTO sessions (id,completedAt,level,rulesVersion,hits,misses,falseAlarms,correctRejections,positionOutcomes) VALUES ('old$rule',1750000000000,2,$rule,4,2,3,11,'${if(rule==3) "HHHHMMFFFCCCCCCCCCCC" else ""}')")
            val legacy=store.load();assertEquals(3,legacy.size)
            legacy.forEach { assertEquals(20,it.sessionLength);assertEquals(75,it.result().accuracy);assertEquals(it.rulesVersion==3,it.outcomes().isNotEmpty()) }
            for(length in listOf(10,20,30,50)) for(mask in 1..7) {
                val row=lengthRecord("$length-$mask",length,mask);store.save(row);store.save(row)
            }
            store.close();assertEquals(31,store.load().size)
            legacy.forEach { assertEquals(it,store.load().first { row -> row.id == it.id }) }
            for(length in listOf(10,20,30,50)) for(mask in 1..7) {
                assertEquals(lengthRecord("$length-$mask",length,mask),store.load().first { it.id == "$length-$mask" })
            }
            rejects { store.save(lengthRecord("10-7",20)) }
            store.clear();assertTrue(store.load().isEmpty())
        } finally { store.close();file.parentFile!!.deleteRecursively() }
    }
    @Test fun invalidRawLengthsAndOutcomesCannotBeReadOverwrittenOrCleared()=runBlocking {
        val changes=listOf("sessionLength=11","sessionLength=10.5","sessionLength='long'","sessionLength=x'0a'",
            "sessionLength=9223372036854775807","rulesVersion=3", "sessionLength=20",
            "positionOutcomes=positionOutcomes || char(0)","positionOutcomes='MMMCCCCCCX'",
            "positionOutcomes=CAST(positionOutcomes AS BLOB)","misses=2", "colourOutcomes=''")
        for(change in changes) for(op in listOf("load","save","clear")) {
            val file=file();val store=RoomHistoryStore(context,file)
            try {
                store.save(lengthRecord("bad"));sql(file,"UPDATE sessions SET $change")
                rejects { when(op) { "load"->store.load();"save"->store.save(lengthRecord("new"));else->store.clear() } }
                store.close();val bytes=file.readBytes()
                rejects { store.load() };rejects { store.save(lengthRecord("new")) };rejects { store.clear() }
                assertArrayEquals(change,bytes,file.readBytes())
            } finally { store.close();file.parentFile!!.deleteRecursively() }
        }
    }
    @Test fun lengthPreferencesRoundTripAndRecoverOnlyInvalidLength()=runBlocking {
        val file=File(context.cacheDir,"length-settings-${System.nanoTime()}.preferences_pb");val job=SupervisorJob()
        try {
            val data=PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job),produceFile={file})
            val store=StoredLevelSettings(data);assertEquals(20,store.load().sessionLength)
            for(length in listOf(10,20,30,50)) { store.save(3,7,8,length);assertEquals(LoadedLevel(3,modeMask=7,intervalSeconds=8,sessionLength=length),store.load()) }
            for(bad in listOf(0,11,100)) {
                data.edit { it[intPreferencesKey("session_length")]=bad }
                assertEquals(LoadedLevel(3,modeMask=7,intervalSeconds=8,lengthReset=true),store.load())
            }
            data.edit { it[stringPreferencesKey("session_length")]="long" }
            assertEquals(LoadedLevel(3,modeMask=7,intervalSeconds=8,lengthReset=true),store.load())
        } finally { job.cancelAndJoin();file.delete() }
    }
}
