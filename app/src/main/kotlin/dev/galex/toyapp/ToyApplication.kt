package dev.galex.toyapp

import android.app.Application

class ToyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Real in src/debug, a no-op in src/release. See ProbeStarter.kt in both source sets.
        startProbeIfDebug()
    }
}
