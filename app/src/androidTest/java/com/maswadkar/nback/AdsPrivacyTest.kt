package com.maswadkar.nback

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.RequestConfiguration
import com.maswadkar.nback.ads.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AdsPrivacyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Consent : ConsentInformation {
        var allowed = false
        var status = ConsentInformation.ConsentStatus.NOT_REQUIRED
        var optionsStatus = ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED
        var updates = 0
        var parameters: ConsentRequestParameters? = null
        var success: ConsentInformation.OnConsentInfoUpdateSuccessListener? = null
        var failure: ConsentInformation.OnConsentInfoUpdateFailureListener? = null
        override fun requestConsentInfoUpdate(activity: Activity, params: ConsentRequestParameters,
            ok: ConsentInformation.OnConsentInfoUpdateSuccessListener, error: ConsentInformation.OnConsentInfoUpdateFailureListener) {
            updates++; parameters = params; success = ok; failure = error
        }
        override fun canRequestAds() = allowed
        override fun getConsentStatus() = status
        override fun getPrivacyOptionsRequirementStatus() = optionsStatus
        override fun isConsentFormAvailable() = true
        override fun reset() { allowed = false }
    }
    @Test fun privacyNoticeIsAvailableWithoutAdsOrNetwork() {
        compose.onNodeWithTag("privacy").performScrollTo().performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("start").performScrollTo().assertIsDisplayed()
    }
    @CriticalCi @Test fun consentAndRequestConfigurationAreIndependentAndPermissionGated() {
        compose.runOnIdle {
            val consent = Consent(); var initialized = 0; var config: RequestConfiguration? = null
            val runtime = AdsRuntime(compose.activity, true, consent,
                initializeAds = { _, done -> initialized++; done() }, configure = { config = it })
            runtime.initialize(compose.activity); assertEquals(0, initialized)
            runtime.refresh(compose.activity); runtime.refresh(compose.activity)
            assertEquals(1, consent.updates); assertTrue(consent.parameters!!.isTagForUnderAgeOfConsent)
            consent.failure!!.onConsentInfoUpdateFailure(FormError(1, "test"))
            runtime.initialize(compose.activity); assertEquals(0, initialized)
            consent.allowed = true; runtime.initialize(compose.activity)
            assertEquals(1, initialized)
            assertEquals(AgeRestrictedTreatment.CHILD, config!!.ageRestrictedTreatment)
            assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_PG, config.maxAdContentRating)
        }
    }
    @CriticalCi @Test fun lateLoadsPrivacyChangesAndDeferredFormsNeverInterruptPlay() {
        compose.runOnIdle {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                val consent = Consent(); consent.allowed = true
                val runtime = AdsRuntime(compose.activity, true, consent, { _, done -> done() }, {})
                runtime.refresh(compose.activity); consent.success!!.onConsentInfoUpdateSuccess()
                var home = true; var loads = 0; var shown = 0
                var loaded: ((LoadedAd?) -> Unit)? = null
                var formLoaded: ((PrivacyForm?) -> Unit)? = null
                val forms = object : PrivacyForms {
                    override fun load(context: Context, done: (PrivacyForm?) -> Unit) { formLoaded = done }
                    override fun options(activity: Activity, done: (Boolean) -> Unit) { done(false) }
                }
                val coordinator = AdCoordinator(object : AdQuotaStore {
                    override suspend fun load() = AdQuota()
                    override suspend fun save(value: AdQuota) = Unit
                }, scope) { 0 }
                val adapter = GoogleAdSurface(compose.activity, runtime, coordinator, { true }, { home }, { null }, {},
                    loader = AdLoader { _, _, request, done ->
                        assertEquals("1", request.getNetworkExtrasBundle(AdMobAdapter::class.java)!!.getString("npa"))
                        loads++; loaded = done
                    }, forms = forms)
                adapter.service(); adapter.service(); assertTrue(loads > 0)
                val late = loaded!!
                consent.allowed = false; runtime.changed(); adapter.service()
                late(object : LoadedAd {
                    override fun show(activity: Activity, shown: () -> Unit, finished: (Boolean) -> Unit) { fail("Stale ad presented") }
                })
                assertFalse(adapter.ready()); assertEquals(0, shown)
                consent.status = ConsentInformation.ConsentStatus.REQUIRED; adapter.service()
                home = false
                formLoaded!!(PrivacyForm { _, done -> shown++; done(false) })
                assertEquals(0, shown)
                home = true; adapter.service(); assertEquals(1, shown)
                adapter.destroy()
            } finally { scope.cancel() }
        }
    }
    @Test fun formFailuresBackOffAndPrivacyOptionsRequireExplicitHomeAction() {
        compose.runOnIdle {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            try {
                val consent = Consent().apply { status = ConsentInformation.ConsentStatus.REQUIRED }
                val runtime = AdsRuntime(compose.activity, true, consent, { _, done -> done() }, {})
                runtime.refresh(compose.activity); consent.success!!.onConsentInfoUpdateSuccess()
                var clock = 0L; var home = true; var formsShown = 0; var optionsShown = 0
                val coordinator = AdCoordinator(object : AdQuotaStore {
                    override suspend fun load() = AdQuota()
                    override suspend fun save(value: AdQuota) = Unit
                }, scope) { clock }
                val forms = object : PrivacyForms {
                    override fun load(context: Context, done: (PrivacyForm?) -> Unit) {
                        done(PrivacyForm { _, finished -> formsShown++; finished(true) })
                    }
                    override fun options(activity: Activity, done: (Boolean) -> Unit) { optionsShown++; done(false) }
                }
                val adapter = GoogleAdSurface(compose.activity, runtime, coordinator, { true }, { home }, { null }, {},
                    loader = AdLoader { _, _, _, _ -> fail("Consent does not permit ads") }, forms = forms, now = { clock })
                adapter.service(); assertEquals(1, formsShown)
                repeat(3) { adapter.service() }; assertEquals(1, formsShown)
                clock = 59_999; adapter.service(); assertEquals(1, formsShown)
                clock = 60_000; adapter.service(); assertEquals(2, formsShown)
                adapter.privacyOptions(); assertEquals(0, optionsShown)
                consent.optionsStatus = ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                home = false; adapter.privacyOptions(); assertEquals(0, optionsShown)
                home = true; adapter.privacyOptions(); assertEquals(1, optionsShown)
                adapter.destroy()
            } finally { scope.cancel() }
        }
    }
}
