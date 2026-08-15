package dev.galex.toyapp

import android.app.Application
import dev.galex.toyapp.probe.ProbeConfig
import dev.galex.toyapp.probe.ProbeHooks
import dev.galex.toyapp.probe.startProbe
import dev.galex.toyapp.ui.NavBridge

/**
 * The debug half of the source set split. This file can reference the probe at all only because
 * the module arrives through `debugImplementation` in app/build.gradle.kts.
 */
fun Application.startProbeIfDebug() {
    startProbe(
        application = this,
        config = ProbeConfig(
            appName = "Toy App",
            versionName = BuildConfig.VERSION_NAME,
            packageName = BuildConfig.APPLICATION_ID,
            port = 4242,
        ),
        hooks = ProbeHooks(
            breadcrumb = { NavBridge.breadcrumb },
            navigationMap = { AppNavigationMap },
        ),
    )
}
