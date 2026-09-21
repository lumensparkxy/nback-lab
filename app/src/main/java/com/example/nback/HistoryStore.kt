package com.example.nback

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
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
import com.example.nback.engine.StimulusType
import com.example.nback.engine.activeTypes
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
    @ColumnInfo(defaultValue = "1") val modeMask: Int = 1,
    @ColumnInfo(defaultValue = "0") val colourHits: Int = 0,
    @ColumnInfo(defaultValue = "0") val colourMisses: Int = 0,
    @ColumnInfo(defaultValue = "0") val colourFalseAlarms: Int = 0,
    @ColumnInfo(defaultValue = "0") val colourCorrectRejections: Int = 0,
    @ColumnInfo(defaultValue = "0") val numberHits: Int = 0,
    @ColumnInfo(defaultValue = "0") val numberMisses: Int = 0,
    @ColumnInfo(defaultValue = "0") val numberFalseAlarms: Int = 0,
    @ColumnInfo(defaultValue = "0") val numberCorrectRejections: Int = 0,
) {
    fun result() = SessionResult(hits, misses, falseAlarms, correctRejections)
    fun results(): Map<StimulusType, SessionResult> = activeTypes(modeMask).associateWith { counts(it) }
    private fun counts(type: StimulusType) = when (type) {
        StimulusType.POSITION -> result()
        StimulusType.COLOUR -> SessionResult(colourHits, colourMisses, colourFalseAlarms, colourCorrectRejections)
        StimulusType.NUMBER -> SessionResult(numberHits, numberMisses, numberFalseAlarms, numberCorrectRejections)
    }
    fun validated(): HistoryRecord {
        require(id.isNotBlank() && level in 1..3 && rulesVersion in 1..2)
        require(modeMask in 1..7 && (rulesVersion != 1 || modeMask == 1))
        StimulusType.entries.forEach { type ->
            val result = counts(type)
            require(listOf(result.hits, result.misses, result.falseAlarms, result.correctRejections).all { it >= 0 })
            if (modeMask and type.bit != 0) {
                require(result.hits.toLong() + result.misses == 6L && result.falseAlarms.toLong() + result.correctRejections == 14L)
            } else require(result == SessionResult())
        }
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
private const val INVALID_V1_ROW = "typeof(id) != 'text' OR trim(id) = '' OR " +
    "typeof(completedAt) != 'integer' OR completedAt < -62135596800000 OR completedAt > 253402300799999 OR " +
    "typeof(level) != 'integer' OR level NOT BETWEEN 1 AND 3 OR " +
    "typeof(rulesVersion) != 'integer' OR rulesVersion != 1 OR " +
    "typeof(hits) != 'integer' OR hits NOT BETWEEN 0 AND 6 OR " +
    "typeof(misses) != 'integer' OR misses NOT BETWEEN 0 AND 6 OR hits + misses != 6 OR " +
    "typeof(falseAlarms) != 'integer' OR falseAlarms NOT BETWEEN 0 AND 14 OR " +
    "typeof(correctRejections) != 'integer' OR correctRejections NOT BETWEEN 0 AND 14 OR " +
    "falseAlarms + correctRejections != 14"

private const val INVALID_ROW = "typeof(id) != 'text' OR trim(id) = '' OR " +
    "typeof(completedAt) != 'integer' OR completedAt < -62135596800000 OR completedAt > 253402300799999 OR " +
    "typeof(level) != 'integer' OR level NOT BETWEEN 1 AND 3 OR " +
    "typeof(rulesVersion) != 'integer' OR rulesVersion NOT IN (1,2) OR " +
    "typeof(modeMask) != 'integer' OR modeMask NOT BETWEEN 1 AND 7 OR (rulesVersion = 1 AND modeMask != 1) OR " +
    "typeof(hits) != 'integer' OR hits NOT BETWEEN 0 AND 6 OR " +
    "typeof(misses) != 'integer' OR misses NOT BETWEEN 0 AND 6 OR " +
    "typeof(falseAlarms) != 'integer' OR falseAlarms NOT BETWEEN 0 AND 14 OR " +
    "typeof(correctRejections) != 'integer' OR correctRejections NOT BETWEEN 0 AND 14 OR " +
    "((modeMask & 1) != 0 AND (hits + misses != 6 OR falseAlarms + correctRejections != 14)) OR " +
    "((modeMask & 1) = 0 AND (hits != 0 OR misses != 0 OR falseAlarms != 0 OR correctRejections != 0)) OR " +
    "typeof(colourHits) != 'integer' OR colourHits NOT BETWEEN 0 AND 6 OR " +
    "typeof(colourMisses) != 'integer' OR colourMisses NOT BETWEEN 0 AND 6 OR " +
    "typeof(colourFalseAlarms) != 'integer' OR colourFalseAlarms NOT BETWEEN 0 AND 14 OR " +
    "typeof(colourCorrectRejections) != 'integer' OR colourCorrectRejections NOT BETWEEN 0 AND 14 OR " +
    "((modeMask & 2) != 0 AND (colourHits + colourMisses != 6 OR colourFalseAlarms + colourCorrectRejections != 14)) OR " +
    "((modeMask & 2) = 0 AND (colourHits != 0 OR colourMisses != 0 OR colourFalseAlarms != 0 OR colourCorrectRejections != 0)) OR " +
    "typeof(numberHits) != 'integer' OR numberHits NOT BETWEEN 0 AND 6 OR " +
    "typeof(numberMisses) != 'integer' OR numberMisses NOT BETWEEN 0 AND 6 OR " +
    "typeof(numberFalseAlarms) != 'integer' OR numberFalseAlarms NOT BETWEEN 0 AND 14 OR " +
    "typeof(numberCorrectRejections) != 'integer' OR numberCorrectRejections NOT BETWEEN 0 AND 14 OR " +
    "((modeMask & 4) != 0 AND (numberHits + numberMisses != 6 OR numberFalseAlarms + numberCorrectRejections != 14)) OR " +
    "((modeMask & 4) = 0 AND (numberHits != 0 OR numberMisses != 0 OR numberFalseAlarms != 0 OR numberCorrectRejections != 0))"

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

@Database(entities = [HistoryRecord::class], version = 2, exportSchema = true)
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
            .addMigrations(MIGRATION_1_2)
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
        internal const val V1_IDENTITY = "a865fd8f7ff85115a8d5954391aa19d1"
        internal const val IDENTITY = "e68ad41ba3d0013275bdbdb7a0293598"
        private val addedColumns = listOf("modeMask", "colourHits", "colourMisses", "colourFalseAlarms",
            "colourCorrectRejections", "numberHits", "numberMisses", "numberFalseAlarms", "numberCorrectRejections")
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                addedColumns.forEach { name ->
                    connection.prepare("ALTER TABLE sessions ADD COLUMN $name INTEGER NOT NULL DEFAULT ${if (name == "modeMask") 1 else 0}").use { it.step() }
                }
            }
        }
        internal fun preflight(file: File) {
            if (!file.exists()) return
            require(file.length() > 0) { "Empty history store" }
            BundledSQLiteDriver().open(file.absolutePath, SQLITE_OPEN_READONLY).use { connection ->
                connection.prepare("PRAGMA quick_check").use { check ->
                    require(check.step() && check.getText(0) == "ok") { "Damaged history store" }
                }
                val schemaVersion = connection.prepare("PRAGMA user_version").use { version ->
                    require(version.step())
                    version.getLong(0).also { require(it in 1L..2L) { "Unsupported history schema" } }
                }
                connection.prepare("SELECT identity_hash FROM room_master_table WHERE id = 42").use { identity ->
                    require(identity.step() && identity.getText(0) == if (schemaVersion == 1L) V1_IDENTITY else IDENTITY) { "Incompatible history schema" }
                }
                val columns = linkedMapOf<String, String>()
                connection.prepare("PRAGMA table_info(sessions)").use { fields ->
                    while (fields.step()) {
                        val name = fields.getText(1)
                        require(fields.getLong(3) == 1L)
                        if (schemaVersion == 2L && name in addedColumns) {
                            require(!fields.isNull(4) && fields.getText(4) == if (name == "modeMask") "1" else "0")
                        } else require(fields.isNull(4))
                        require(fields.getLong(5) == if (name == "id") 1L else 0L)
                        columns[name] = fields.getText(2)
                    }
                }
                val expectedColumns = mapOf("id" to "TEXT", "completedAt" to "INTEGER", "level" to "INTEGER",
                    "rulesVersion" to "INTEGER", "hits" to "INTEGER", "misses" to "INTEGER",
                    "falseAlarms" to "INTEGER", "correctRejections" to "INTEGER") +
                    if (schemaVersion == 2L) addedColumns.associateWith { "INTEGER" } else emptyMap()
                require(columns == expectedColumns) { "Incompatible history columns" }
                val predicate = if (schemaVersion == 1L) INVALID_V1_ROW else INVALID_ROW
                connection.prepare("SELECT EXISTS(SELECT 1 FROM sessions WHERE $predicate)").use { invalid ->
                    require(invalid.step() && invalid.getLong(0) == 0L) { "Invalid history data" }
                }
                // Raw predicates enforce types/ranges before Room mapping or migration.
                connection.prepare("SELECT id FROM sessions").use { rows ->
                    while (rows.step()) require(rows.getText(0).isNotBlank())
                }
            }
        }
    }
}
