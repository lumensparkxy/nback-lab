package com.maswadkar.nback

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class NBackTestApplication : NBackApplication() {
    override val monetizationEnabled = false
}
class NBackTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, NBackTestApplication::class.java.name, context)
}
