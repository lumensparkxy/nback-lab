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

class MainActivity : ComponentActivity() {
    internal val session: SessionViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SessionViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return SessionViewModel(StoredLevelSettings.from(applicationContext), (application as NBackApplication).history) as T
            }
        })[SessionViewModel::class.java]
    }
    private val lifecycleHandler = Handler(Looper.getMainLooper())
    private var pendingPause: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Consult current session state even before the next Compose frame.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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

    internal fun keepScreenAwake(playing: Boolean) {
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
