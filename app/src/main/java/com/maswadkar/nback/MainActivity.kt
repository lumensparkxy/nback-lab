package com.maswadkar.nback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maswadkar.nback.ads.GoogleAdSurface
import com.maswadkar.nback.engine.SessionScreen

class MainActivity : ComponentActivity() {
    internal val session: SessionViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SessionViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return SessionViewModel(StoredLevelSettings.from(applicationContext), (application as NBackApplication).history,
                    completedNormal = (application as NBackApplication).ads::completed) as T
            }
        })[SessionViewModel::class.java]
    }
    internal val adsRuntime get() = (application as NBackApplication).adsRuntime
    private val adCoordinator get() = (application as NBackApplication).ads
    internal val adSurface by lazy { GoogleAdSurface(this, adsRuntime, adCoordinator,
        isResumed = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
        isHome = { session.state.screen == SessionScreen.HOME && !session.historyNavigation.open },
        result = { session.resultId.takeIf { session.state.screen == SessionScreen.RESULTS && !session.historyNavigation.open } },
        goHome = { id -> if (session.resultId == id && session.state.screen == SessionScreen.RESULTS && !session.historyNavigation.open) session.home() }) }
    internal fun resultsHome() { session.resultId?.let(adCoordinator::resultsHome) }
    private val lifecycleHandler = Handler(Looper.getMainLooper())
    private var pendingPause: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adCoordinator.attach(adSurface)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adCoordinator.resume()
                adSurface.service()
            }
        })
        // Consult current session state even before the next Compose frame.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                adCoordinator.invalidate()
                if (session.handlesBack) {
                    session.back()
                } else {
                    isEnabled = false
                    try { onBackPressedDispatcher.onBackPressed() } finally { isEnabled = true }
                }
            }
        })
        setContent { NBackApp(session) }
    }

    override fun onResume() {
        super.onResume()
        pendingPause?.let { lifecycleHandler.removeCallbacks(it); it.run() }
        pendingPause = null
        session.resume()
    }

    override fun onPause() {
        adCoordinator.invalidate()
        adSurface.invalidate()
        session.pauseTicker()
        // Decide after the synchronous lifecycle transition, so configuration
        // relaunch and genuine loss of resumed state can be distinguished.
        val decision = Runnable {
            if (!isChangingConfigurations) session.interrupt()
            pendingPause = null
        }
        pendingPause = decision
        lifecycleHandler.post(decision)
        super.onPause()
    }

    override fun onDestroy() {
        adSurface.destroy()
        super.onDestroy()
    }

    internal fun keepScreenAwake(playing: Boolean) {
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
