package com.maswadkar.nback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class MemoryHistoryStore : HistoryStore {
    val records = mutableMapOf<String, HistoryRecord>()
    override suspend fun load() = records.values.sortedWith(compareByDescending<HistoryRecord> { it.completedAt }.thenBy { it.id })
    override suspend fun save(record: HistoryRecord) {
        check(records[record.id]?.let { it == record } != false)
        records[record.id] = record
    }
    override suspend fun clear() { records.clear() }
}
internal fun testHistory(scope: CoroutineScope) = HistoryCoordinator(MemoryHistoryStore(), scope)

internal fun sampleRecord(id: String = "one", level: Int = 2, time: Long = 1_750_000_000_000L) =
    HistoryRecord(id, time, level, hits = 4, misses = 2, falseAlarms = 3, correctRejections = 11)
