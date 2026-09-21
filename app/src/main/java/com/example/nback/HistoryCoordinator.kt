package com.example.nback

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class SaveStatus { PENDING, SAVED, FAILED, REMOVED }
data class PendingResult(val record: HistoryRecord, val status: SaveStatus)
enum class HistoryLoad { LOADING, READY, FAILED }
data class HistoryState(
    val entries: Map<String, PendingResult> = emptyMap(),
    val records: List<HistoryRecord> = emptyList(),
    val load: HistoryLoad = HistoryLoad.LOADING,
    val clearing: Boolean = false,
    val clearFailed: Boolean = false,
) {
    val pending: Int get() = entries.values.count { it.status == SaveStatus.PENDING }
    val failed: Int get() = entries.values.count { it.status == SaveStatus.FAILED }
    val canClear: Boolean get() = load == HistoryLoad.READY && !clearing && (records.isNotEmpty() || pending + failed > 0)
}

/** Main-thread confined commands; one application-owned queue orders all database work. */
class HistoryCoordinator(private val store: HistoryStore, scope: CoroutineScope) {
    var state by mutableStateOf(HistoryState())
        private set
    private sealed interface Command {
        data class Save(val record: HistoryRecord) : Command
        data class Clear(val cutoff: Set<String>) : Command
        data object Load : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var loadQueued = false
    init {
        scope.launch {
            for (command in commands) when (command) {
                is Command.Save -> {
                    try { store.save(command.record); status(command.record.id, SaveStatus.SAVED) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status(command.record.id, SaveStatus.FAILED) }
                    read()
                }
                is Command.Clear -> {
                    try {
                        store.clear()
                        state = state.copy(entries = state.entries.mapValues { (id, entry) ->
                            if (id in command.cutoff) entry.copy(status = SaveStatus.REMOVED) else entry
                        }, records = emptyList(), load = HistoryLoad.READY, clearing = false, clearFailed = false)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { state = state.copy(clearing = false, clearFailed = true) }
                    read()
                }
                Command.Load -> { loadQueued = false; read() }
            }
        }
        reload()
    }
    private fun status(id: String, status: SaveStatus) {
        val entry = state.entries.getValue(id)
        state = state.copy(entries = state.entries + (id to entry.copy(status = status)))
    }
    private suspend fun read() {
        try { state = state.copy(records = store.load(), load = HistoryLoad.READY) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state = state.copy(records = emptyList(), load = HistoryLoad.FAILED) }
    }
    fun capture(record: HistoryRecord) {
        record.validated()
        val existing = state.entries[record.id]
        if (existing != null) { check(existing.record == record); return }
        state = state.copy(entries = state.entries + (record.id to PendingResult(record, SaveStatus.PENDING)))
        check(commands.trySend(Command.Save(record)).isSuccess)
    }
    fun retry(id: String? = null) {
        if (state.clearing) return
        state.entries.values.filter { it.status == SaveStatus.FAILED && (id == null || it.record.id == id) }.forEach {
            status(it.record.id, SaveStatus.PENDING)
            check(commands.trySend(Command.Save(it.record)).isSuccess)
        }
    }
    fun reload() {
        if (loadQueued) return
        loadQueued = true
        state = state.copy(load = HistoryLoad.LOADING, records = emptyList())
        check(commands.trySend(Command.Load).isSuccess)
    }
    fun clear() {
        if (!state.canClear) return
        val cutoff = state.entries.keys.toSet()
        state = state.copy(clearing = true, clearFailed = false)
        check(commands.trySend(Command.Clear(cutoff)).isSuccess)
    }
    fun dismissClearFailure() { state = state.copy(clearFailed = false) }
}

class NBackApplication : Application() {
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val history: HistoryCoordinator by lazy {
        HistoryCoordinator(RoomHistoryStore(this, getDatabasePath("history.db")), historyScope)
    }
}
