package com.maswadkar.nback.ads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AdCoordinatorTest {
    private class Store(var value: AdQuota = AdQuota()) : AdQuotaStore {
        var broken = false
        var blocked: CompletableDeferred<Unit>? = null
        override suspend fun load(): AdQuota { check(!broken); return value }
        override suspend fun save(value: AdQuota) { blocked?.await(); check(!broken); this.value = value }
    }
    private class Surface : AdSurface {
        var id: String? = "6"
        var foreground = true
        var loaded = true
        var shows = 0
        var homes = 0
        var shown: () -> Unit = {}
        var finished: (Boolean) -> Unit = {}
        override fun currentResult() = id
        override fun mayPresent() = foreground
        override fun ready() = loaded
        override fun show(shown: () -> Unit, finished: (Boolean) -> Unit) {
            shows++; this.shown = shown; this.finished = finished
        }
        override fun home(resultId: String) { if (id == resultId) { homes++; id = null } }
    }
    private suspend fun drain() {
        val job = currentCoroutineContext()[Job]!!
        while (job.children.any()) job.children.toList().forEach { it.join() }
    }
    @Test fun graceAndDuplicateCompletionsAreIndependentOfHistory() = runBlocking {
        val store = Store(); val ads = AdCoordinator(store, this) { 0 }
        drain()
        (1..3).forEach { ads.completed("$it"); ads.completed("$it"); drain() }
        assertEquals(AdQuota(3, 0, "3"), store.value)
        (4..6).forEach { ads.completed("$it"); drain() }
        assertEquals(3, store.value.completed)
        ads.completed("6"); drain(); assertEquals(3, store.value.completed)
    }
    @Test fun sixthCompletionAndExactClockBoundaryAreRequired() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 2, "5"))
        val ads = AdCoordinator(store, this) { clock }; val ui = Surface(); ads.attach(ui); drain()
        clock = 300_000; ui.id = "5"; ads.resultsHome("5"); drain(); assertEquals(0, ui.shows)
        ads.completed("6"); drain(); clock = 299_999; ui.id = "6"; ads.resultsHome("6"); drain()
        assertEquals(0, ui.shows)
        ads.completed("7"); drain(); clock = 300_000; ui.id = "7"; ads.resultsHome("7"); drain()
        assertEquals(1, ui.shows); assertEquals(0, store.value.completed)
    }
    @Test fun laterAdsRequireBothThreeCompletionsAndCooldown() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 3, "6")); val ads = AdCoordinator(store, this) { clock }
        val ui = Surface(); ads.attach(ui); drain(); clock = 300_000
        ads.resultsHome("6"); drain(); ui.shown(); ui.finished(false); drain()
        (7..8).forEach { ads.completed("$it"); drain() }
        clock = 600_000; ui.id = "8"; ads.resultsHome("8"); drain(); assertEquals(1, ui.shows)
        ads.completed("9"); drain(); clock = 599_999; ui.id = "9"; ads.resultsHome("9"); drain()
        assertEquals(1, ui.shows)
        ads.completed("10"); drain(); clock = 600_000; ui.id = "10"; ads.resultsHome("10"); drain()
        assertEquals(2, ui.shows)
    }
    @Test fun definiteFailureRestoresQuotaAndCooldownAndTerminalIsOnce() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 3, "6")); val ads = AdCoordinator(store, this) { clock }
        val ui = Surface(); ads.attach(ui); drain(); clock = 300_000
        ads.resultsHome("6"); ads.resultsHome("6"); drain()
        assertEquals(1, ui.shows)
        ui.finished(true); ui.finished(false); drain()
        assertEquals(3, store.value.completed); assertEquals(1, ui.homes)
        ui.id = "7"; ads.completed("7"); drain(); ads.resultsHome("7"); drain()
        assertEquals(2, ui.shows)
    }
    @Test fun noFillAndUnavailableSurfaceNeverConsumeQuota() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 3, "6")); val ads = AdCoordinator(store, this) { clock }
        val ui = Surface(); ui.loaded = false; ads.attach(ui); drain(); clock = 300_000
        ads.resultsHome("6"); drain(); assertEquals(1, ui.homes); assertEquals(3, store.value.completed)
        ui.loaded = true; ads.resume(); drain(); assertEquals(0, ui.shows)
    }
    @Test fun navigationWhileReservationIsPendingCannotShowLate() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 3, "6")); val ads = AdCoordinator(store, this) { clock }
        val ui = Surface(); ads.attach(ui); drain(); clock = 300_000
        store.blocked = CompletableDeferred(); ads.resultsHome("6"); yield()
        ui.id = null; ads.invalidate(); store.blocked!!.complete(Unit); drain()
        assertEquals(0, ui.shows); assertEquals(0, ui.homes)
    }
    @Test fun cancelledReservationStillNavigatesAfterResumeOnSameResult() = runBlocking {
        var clock = 0L; val store = Store(AdQuota(3, 3, "6")); val ads = AdCoordinator(store, this) { clock }
        val ui = Surface(); ads.attach(ui); drain(); clock = 300_000
        store.blocked = CompletableDeferred(); ads.resultsHome("6"); yield()
        ui.foreground = false; ads.invalidate(); store.blocked!!.complete(Unit); drain()
        ads.resume(); assertEquals(0, ui.homes)
        ui.foreground = true; ads.resume(); drain()
        assertEquals(1, ui.homes); assertEquals(0, ui.shows)
    }
    @Test fun dismissalBeforeResumeWaitsAndDeliversOnlyOnce() = runBlocking {
        var clock = 0L; val ads = AdCoordinator(Store(AdQuota(3, 3, "6")), this) { clock }
        val ui = Surface(); ads.attach(ui); drain(); clock = 300_000
        ads.resultsHome("6"); drain(); ui.foreground = false; ui.finished(false); drain()
        ads.resume(); assertEquals(0, ui.homes)
        ui.foreground = true; ads.resume(); ads.resume(); drain(); assertEquals(1, ui.homes)
    }
    @Test fun destructionCannotReplayAndDismissalNavigatesOnNewSurface() = runBlocking {
        var clock = 0L; val ads = AdCoordinator(Store(AdQuota(3, 3, "6")), this) { clock }
        val old = Surface(); ads.attach(old); drain(); clock = 300_000
        ads.resultsHome("6"); drain(); ads.detach(old)
        old.finished(false); drain(); assertEquals(0, old.homes)
        val next = Surface(); ads.attach(next); ads.resume(); drain()
        assertEquals(1, next.homes); assertEquals(0, next.shows)
    }
    @Test fun restartRequiresNewGuardEvenWithPersistedQuota() = runBlocking {
        var clock = 999_999L; val ads = AdCoordinator(Store(AdQuota(3, 3, "6")), this) { clock }
        val ui = Surface(); ads.attach(ui); drain()
        clock += 299_999; ads.resultsHome("6"); drain(); assertEquals(0, ui.shows)
    }
    @Test fun corruptOrFailedStoreNeverBlocksHomeOrClearsStoredData() = runBlocking {
        val store = Store(AdQuota(4, 3, "bad")); var clock = 0L
        val ads = AdCoordinator(store, this) { clock }; val ui = Surface(); ads.attach(ui); drain()
        clock = 300_000; ads.resultsHome("6"); drain()
        assertEquals(0, ui.shows); assertEquals(1, ui.homes); assertEquals(4, store.value.grace)
    }
    @Test fun failedReservationSkipsAd() = runBlocking {
        val store = Store(AdQuota(3, 3, "6")); var clock = 0L
        val ads = AdCoordinator(store, this) { clock }; val ui = Surface(); ads.attach(ui); drain()
        store.broken = true; clock = 300_000; ads.resultsHome("6"); drain()
        assertEquals(0, ui.shows); assertEquals(1, ui.homes)
    }
}
