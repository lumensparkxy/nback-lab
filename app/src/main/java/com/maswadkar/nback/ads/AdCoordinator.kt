package com.maswadkar.nback.ads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** No Android, gameplay, history, or SDK dependency. All calls use the UI dispatcher. */
data class AdQuota(val grace: Int = 0, val completed: Int = 0, val lastRun: String = "") {
    fun checked(): AdQuota = apply {
        require(grace in 0..3 && completed in 0..3 && (grace == 3 || completed == 0))
    }
    fun complete(id: String): AdQuota {
        require(id.isNotBlank())
        if (id == lastRun) return this
        return if (grace < 3) copy(grace = grace + 1, lastRun = id)
        else copy(completed = (completed + 1).coerceAtMost(3), lastRun = id)
    }
}

interface AdQuotaStore {
    suspend fun load(): AdQuota
    suspend fun save(value: AdQuota)
}

interface AdSurface {
    fun currentResult(): String?
    fun mayPresent(): Boolean
    fun ready(): Boolean
    fun show(shown: () -> Unit, finished: (failed: Boolean) -> Unit)
    fun home(resultId: String)
}

class AdCoordinator(
    private val store: AdQuotaStore,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    companion object { const val COOLDOWN_MS = 300_000L }
    private val started = now()
    private var lastShow = started
    private var quota = AdQuota()
    private var healthy = false
    private val mutex = Mutex()
    private var surface: AdSurface? = null
    private var epoch = 0L
    private var request: Job? = null
    private var presenting = false
    private var destination: String? = null
    private var operation = 0L
    private var usedResult: String? = null
    private var reservingResult: String? = null

    init {
        scope.launch {
            mutex.withLock {
                try { quota = store.load().checked(); healthy = true }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { healthy = false }
            }
        }
    }

    fun completed(id: String) {
        scope.launch {
            mutex.withLock {
                if (!healthy) return@withLock
                val next = quota.complete(id)
                if (next != quota) persist(next)
            }
        }
    }

    private suspend fun persist(next: AdQuota): Boolean = try {
        withTimeout(250) { store.save(next.checked()) }
        quota = next
        true
    } catch (e: CancellationException) {
        // A cancelled/uncertain write must not enable another ad in this process.
        healthy = false
        if (e is kotlinx.coroutines.TimeoutCancellationException) false else throw e
    } catch (_: Exception) { healthy = false; false }

    fun attach(value: AdSurface) { surface = value }
    fun detach(value: AdSurface) {
        if (surface === value) { invalidate(); surface = null }
    }
    fun invalidate() {
        epoch++
        if (!presenting) {
            reservingResult?.let { destination = it }
            request?.cancel(); request = null; reservingResult = null
        }
    }
    fun resume() { deliverHome() }
    private fun deliverHome() {
        val id = destination ?: return
        val target = surface ?: return
        if (!target.mayPresent()) return
        destination = null
        target.home(id)
    }

    fun resultsHome(id: String) {
        if (presenting || request?.isActive == true) return
        val target = surface ?: return
        if (target.currentResult() != id) return
        if (usedResult == id) { target.home(id); return }
        usedResult = id
        if (!healthy || !eligible() || !target.mayPresent() || !target.ready()) {
            target.home(id)
            return
        }
        val generation = epoch
        val token = ++operation
        reservingResult = id
        request = scope.launch {
            mutex.withLock {
                val previous = quota
                val previousTime = lastShow
                if (!eligible() || !healthy) { destination = id; deliverHome(); return@withLock }
                if (!persist(quota.copy(completed = 0))) {
                    destination = id; deliverHome(); return@withLock
                }
                lastShow = now()
                // Never retain the Activity/Surface over suspension or replay a stale transition.
                val current = surface
                if (generation != epoch || current == null || current.currentResult() != id ||
                    !current.mayPresent() || !current.ready()) {
                    if (persist(previous)) lastShow = previousTime
                    if (generation == epoch) { destination = id; deliverHome() }
                    return@withLock
                }
                reservingResult = null
                presenting = true
                var terminal = false
                val finish: (Boolean) -> Unit = finish@{ failed ->
                    if (terminal || token != operation) return@finish
                    terminal = true
                    scope.launch {
                        mutex.withLock {
                            if (failed && healthy && persist(previous)) lastShow = previousTime
                            presenting = false
                            destination = id
                            deliverHome()
                        }
                    }
                }
                try {
                    current.show(shown = { if (!terminal && token == operation) lastShow = now() }, finished = finish)
                } catch (_: Exception) { finish(true) }
            }
        }
    }

    private fun eligible(): Boolean = quota.grace == 3 && quota.completed >= 3 &&
        now() - started >= COOLDOWN_MS && now() - lastShow >= COOLDOWN_MS
}
