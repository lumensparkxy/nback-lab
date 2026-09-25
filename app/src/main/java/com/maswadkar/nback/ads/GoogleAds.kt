package com.maswadkar.nback.ads

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.maswadkar.nback.BuildConfig
import java.lang.ref.WeakReference

/** Process state contains only application context, never an Activity or a form. */
class AdsRuntime(
    context: Context,
    val enabled: Boolean,
    val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext),
    private val initializeAds: (Context, () -> Unit) -> Unit = { app, done -> MobileAds.initialize(app) { done() } },
    private val configure: (RequestConfiguration) -> Unit = MobileAds::setRequestConfiguration,
) {
    var revision by mutableIntStateOf(0)
        private set
    var privacyError by mutableStateOf(false)
        private set
    private var requested = false
    var updated = false
        private set
    var initialized = false
        private set
    private var initializing = false

    fun refresh(activity: Activity, retry: Boolean = false) {
        if (!enabled || (requested && !retry)) return
        requested = true
        updated = false
        consent.requestConsentInfoUpdate(activity,
            ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build(),
            { updated = true; privacyError = false; revision++ },
            { updated = true; privacyError = true; revision++ })
    }
    fun canRequest(): Boolean = enabled && updated && consent.canRequestAds()
    fun changed(error: Boolean = false) { privacyError = error; revision++ }
    fun initialize(context: Context) {
        if (!canRequest() || initialized || initializing) return
        configure(RequestConfiguration.Builder()
            .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build())
        initializing = true
        initializeAds(context.applicationContext) {
            initialized = true; initializing = false; revision++
        }
    }
}

interface LoadedAd {
    fun show(activity: Activity, shown: () -> Unit, finished: (Boolean) -> Unit)
}
fun interface AdLoader {
    fun load(context: Context, unit: String, request: AdRequest, done: (LoadedAd?) -> Unit)
}
private val googleLoader = AdLoader { context, unit, request, done ->
    InterstitialAd.load(context, unit, request, object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) = done(object : LoadedAd {
            override fun show(activity: Activity, shown: () -> Unit, finished: (Boolean) -> Unit) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() = shown()
                    override fun onAdDismissedFullScreenContent() = finished(false)
                    override fun onAdFailedToShowFullScreenContent(error: AdError) = finished(true)
                }
                ad.show(activity)
            }
        })
        override fun onAdFailedToLoad(error: LoadAdError) = done(null)
    })
}
fun interface PrivacyForm { fun show(activity: Activity, done: (Boolean) -> Unit) }
interface PrivacyForms {
    fun load(context: Context, done: (PrivacyForm?) -> Unit)
    fun options(activity: Activity, done: (Boolean) -> Unit)
}
private val googleForms = object : PrivacyForms {
    override fun load(context: Context, done: (PrivacyForm?) -> Unit) =
        UserMessagingPlatform.loadConsentForm(context, { form ->
            done(PrivacyForm { activity, finished -> form.show(activity) { finished(it != null) } })
        }, { done(null) })
    override fun options(activity: Activity, done: (Boolean) -> Unit) =
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { done(it != null) }
}

/** Activity-owned adapter. Loading is never a presentation trigger. */
class GoogleAdSurface(
    private val activity: Activity,
    private val runtime: AdsRuntime,
    private val coordinator: AdCoordinator,
    private val isResumed: () -> Boolean,
    private val isHome: () -> Boolean,
    private val result: () -> String?,
    private val goHome: (String) -> Unit,
    private val loader: AdLoader = googleLoader,
    private val forms: PrivacyForms = googleForms,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : AdSurface {
    private var ad: LoadedAd? = null
    private var loadedAt = 0L
    private var generation = 0L
    private var loading = false
    private var retryAfter = 0L
    private var destroyed = false
    private var formLoading = false
    private var formShowing = false
    private var consentForm: PrivacyForm? = null
    private var lastPermission = false
    private var lastRevision = -1
    private fun foreground(): Boolean = !destroyed && !activity.isFinishing && !activity.isDestroyed && isResumed()
    override fun currentResult(): String? = result()
    override fun mayPresent(): Boolean = foreground() && !formShowing
    override fun ready(): Boolean = runtime.canRequest() && ad != null &&
        now() - loadedAt < 3_600_000L

    fun service() {
        if (!foreground()) return
        if (isHome()) runtime.refresh(activity)
        val allowed = runtime.canRequest()
        if (lastRevision != runtime.revision || lastPermission != allowed) {
            lastRevision = runtime.revision; lastPermission = allowed
            invalidate()
        }
        if (isHome() && runtime.updated &&
            runtime.consent.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
            requiredForm()
            return
        }
        if (!allowed || formShowing || (!isHome() && result() == null)) return
        runtime.initialize(activity.applicationContext)
        if (!runtime.initialized || loading || ready() || now() < retryAfter) return
        ad = null
        loading = true
        val token = generation
        val owner = WeakReference(this)
        val request = AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java,
            Bundle().apply { putString("npa", "1") }).build()
        loader.load(activity.applicationContext, BuildConfig.AD_UNIT_ID, request) { value ->
            owner.get()?.let {
                if (!it.destroyed && token == it.generation && it.runtime.canRequest()) {
                    it.loading = false
                    if (value != null) { it.ad = value; it.loadedAt = it.now() }
                    else it.retryAfter = it.now() + 60_000
                }
            }
        }
    }
    private fun requiredForm() {
        if (formShowing || formLoading || !foreground() || !isHome()) return
        val existing = consentForm
        if (existing != null) {
            consentForm = null; formShowing = true; invalidate()
            val owner = WeakReference(this)
            existing.show(activity) { error -> owner.get()?.let {
                if (error) it.retryAfter = it.now() + 60_000
                it.formShowing = false; it.runtime.changed(error)
            } }
            return
        }
        if (now() < retryAfter) return
        formLoading = true
        val owner = WeakReference(this)
        forms.load(activity.applicationContext) { form -> owner.get()?.let {
            it.formLoading = false
            if (!it.destroyed) {
                if (form != null) {
                    it.consentForm = form
                    if (it.foreground() && it.isHome()) it.requiredForm()
                } else { it.retryAfter = it.now() + 60_000; it.runtime.changed(true) }
            }
        } }
    }
    fun privacyOptions() {
        if (!foreground() || !isHome() || formShowing || formLoading ||
            runtime.consent.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) return
        invalidate(); formShowing = true
        val owner = WeakReference(this)
        forms.options(activity) { error -> owner.get()?.let {
            if (error) it.retryAfter = it.now() + 60_000
            it.formShowing = false; it.runtime.changed(error)
        } }
    }
    fun retryPrivacy() { if (foreground() && isHome()) { retryAfter = 0; invalidate(); runtime.refresh(activity, true) } }
    fun invalidate() { generation++; loading = false; ad = null }
    fun destroy() { destroyed = true; invalidate(); consentForm = null; coordinator.detach(this) }
    override fun home(resultId: String) { goHome(resultId) }
    override fun show(shown: () -> Unit, finished: (Boolean) -> Unit) {
        val value = ad
        ad = null
        if (value == null || !runtime.canRequest() || !foreground()) { finished(true); return }
        value.show(activity, shown, finished)
    }
}
