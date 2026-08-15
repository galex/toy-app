package dev.galex.toyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import dev.galex.toyapp.analytics.LocalAnalytics
import dev.galex.toyapp.analytics.LoggingAnalytics
import dev.galex.toyapp.analytics.recordingIfDebug
import dev.galex.toyapp.ui.ToyApp

class MainActivity : ComponentActivity() {

    // The vendor SDK, wrapped so the debug build records everything on its way out. In a release
    // build recordingIfDebug() hands back the same object, because there is no recorder to wrap.
    private val analytics = LoggingAnalytics().recordingIfDebug()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAnalytics provides analytics) {
                ToyApp()
            }
        }
    }
}
