package com.example.nback

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.example.nback.engine.SessionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@Entity(tableName = "sessions")
data class HistoryRecord(
    @PrimaryKey val id: String,
    val completedAt: Long,
    val level: Int,
    val rulesVersion: Int = 1,
    val hits: Int,
    val misses: Int,
    val falseAlarms: Int,
    val correctRejections: Int,
) {
    fun result() = SessionResult(hits, misses, falseAlarms, correctRejections)
    fun validated(): HistoryRecord {
        require(id.isNotBlank() && level in 1..3 && rulesVersion == 1)
        require(listOf(hits, misses, falseAlarms, correctRejections).all { it >= 0 })
        require(hits.toLong() + misses == 6L && falseAlarms.toLong() + correctRejections == 14L)
        // Four-digit civil years keep Android's locale formatters representable in every zone.
        val year = Instant.ofEpochMilli(completedAt).atOffset(ZoneOffset.UTC).year
        require(year in 1..9999)
        return this
    }
}

interface HistoryStore {
    suspend fun load(): List<HistoryRecord>
    suspend fun save(record: HistoryRecord)
    suspend fun clear()
}

// SQLite affinity is not a type constraint. Reject raw values before Room/getLong can coerce them.
private const val INVALID_ROW = "typeof(id) != 'text' OR trim(id) = '' OR " +
    "typeof(completedAt) != 'integer' OR completedAt < -62135596800000 OR completedAt > 253402300799999 OR " +
    "typeof(level) != 'integer' OR level NOT BETWEEN 1 AND 3 OR " +
    "typeof(rulesVersion) != 'integer' OR rulesVersion != 1 OR " +
    "typeof(hits) != 'integer' OR hits NOT BETWEEN 0 AND 6 OR " +
    "typeof(misses) != 'integer' OR misses NOT BETWEEN 0 AND 6 OR hits + misses != 6 OR " +
    "typeof(falseAlarms) != 'integer' OR falseAlarms NOT BETWEEN 0 AND 14 OR " +
    "typeof(correctRejections) != 'integer' OR correctRejections NOT BETWEEN 0 AND 14 OR " +
    "falseAlarms + correctRejections != 14"

@Dao
abstract class HistoryDao {
    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE " + INVALID_ROW + ")")
    abstract suspend fun hasInvalidRows(): Boolean
    @Transaction
    open suspend fun validatedAll(): List<HistoryRecord> {
        check(!hasInvalidRows()) { "Invalid history data" }
        return all().onEach(HistoryRecord::validated)
    }
    @Transaction
    open suspend fun clearValidated() {
        check(!hasInvalidRows()) { "Invalid history data" }
        deleteAll()
    }
    @Query("SELECT * FROM sessions ORDER BY completedAt DESC, id ASC")
    abstract suspend fun all(): List<HistoryRecord>
    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun find(id: String): HistoryRecord?
    @Insert
    abstract suspend fun insert(record: HistoryRecord)
    @Query("DELETE FROM sessions")
    abstract suspend fun deleteAll()
    @Transaction
    open suspend fun saveIdentical(record: HistoryRecord) {
        check(!hasInvalidRows()) { "Invalid history data" }
        val existing = find(record.id)
        if (existing == null) insert(record) else check(existing == record) { "Conflicting session ID" }
    }
}

@Database(entities = [HistoryRecord::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun sessions(): HistoryDao
}

/** Serialized initialization is retryable; no framework corruption handler or destructive fallback. */
class RoomHistoryStore(private val context: Context, private val file: File) : HistoryStore {
    private val mutex = Mutex()
    private var database: HistoryDatabase? = null
    private fun database(): HistoryDatabase {
        database?.let { return it }
        preflight(file)
        return Room.databaseBuilder<HistoryDatabase>(context.applicationContext, file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build().also { database = it }
    }
    private suspend fun <T> access(block: suspend (HistoryDao) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block(database().sessions()) }
            catch (error: Exception) {
                database?.close(); database = null
                throw error
            }
        }
    }
    override suspend fun load() = access { it.validatedAll() }
    override suspend fun save(record: HistoryRecord) { record.validated(); access { it.saveIdentical(record) } }
    override suspend fun clear() = access { it.clearValidated() }
    suspend fun close() = withContext(Dispatchers.IO) { mutex.withLock { database?.close(); database = null } }

    companion object {
        // Matches the checked-in Room v1 schema; change only with a preserving migration.
        internal const val IDENTITY = "a865fd8f7ff85115a8d5954391aa19d1"
        internal fun preflight(file: File) {
            if (!file.exists()) return
            require(file.length() > 0) { "Empty history store" }
            BundledSQLiteDriver().open(file.absolutePath, SQLITE_OPEN_READONLY).use { connection ->
                connection.prepare("PRAGMA quick_check").use { check ->
                    require(check.step() && check.getText(0) == "ok") { "Damaged history store" }
                }
                connection.prepare("PRAGMA user_version").use { version ->
                    require(version.step() && version.getLong(0) == 1L) { "Unsupported history schema" }
                }
                connection.prepare("SELECT identity_hash FROM room_master_table WHERE id = 42").use { identity ->
                    require(identity.step() && identity.getText(0) == IDENTITY) { "Incompatible history schema" }
                }
                val columns = linkedMapOf<String, String>()
                connection.prepare("PRAGMA table_info(sessions)").use { fields ->
                    while (fields.step()) {
                        val name = fields.getText(1)
                        require(fields.getLong(3) == 1L && fields.isNull(4))
                        require(fields.getLong(5) == if (name == "id") 1L else 0L)
                        columns[name] = fields.getText(2)
                    }
                }
                require(columns == mapOf("id" to "TEXT", "completedAt" to "INTEGER", "level" to "INTEGER",
                    "rulesVersion" to "INTEGER", "hits" to "INTEGER", "misses" to "INTEGER",
                    "falseAlarms" to "INTEGER", "correctRejections" to "INTEGER")) { "Incompatible history columns" }
                connection.prepare("SELECT EXISTS(SELECT 1 FROM sessions WHERE $INVALID_ROW)").use { invalid ->
                    require(invalid.step() && invalid.getLong(0) == 0L) { "Invalid history data" }
                }
                // Read-only validation happens before Room can alter journal mode or open for writes.
                connection.prepare("SELECT id, completedAt, level, rulesVersion, hits, misses, falseAlarms, correctRejections FROM sessions").use { rows ->
                    while (rows.step()) {
                        fun int(column: Int): Int {
                            val value = rows.getLong(column)
                            require(value in Int.MIN_VALUE..Int.MAX_VALUE)
                            return value.toInt()
                        }
                        require((0..7).none { rows.isNull(it) })
                        HistoryRecord(rows.getText(0), rows.getLong(1), int(2), int(3), int(4), int(5), int(6), int(7)).validated()
                    }
                }
            }
        }
    }
}
