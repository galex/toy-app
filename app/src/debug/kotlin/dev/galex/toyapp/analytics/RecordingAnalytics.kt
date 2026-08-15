package dev.galex.toyapp.analytics

import dev.galex.toyapp.probe.AnalyticsRecorder

/**
 * The debug half of the split: every event is recorded on its way out, and still delivered.
 *
 * This file can reference the recorder at all only because the probe module arrives through
 * `debugImplementation`. Its twin in src/release returns the delegate untouched, which is why there
 * is nothing to strip and nothing to forget.
 */
fun Analytics.recordingIfDebug(): Analytics = object : Analytics {
    override fun track(name: String, params: Map<String, String>) {
        AnalyticsRecorder.record(name, params)      // the probe sees it
        this@recordingIfDebug.track(name, params)   // and the vendor SDK still gets it
    }
}
