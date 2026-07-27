package dev.galex.toyapp

import android.app.Application

/**
 * The release half of the source set split, and the reason the probe can't ship by accident.
 *
 * There is nothing to strip here and nothing to forget, because the probe module is not on the
 * release compile classpath at all.
 */
@Suppress("UnusedReceiverParameter")
fun Application.startProbeIfDebug() = Unit
