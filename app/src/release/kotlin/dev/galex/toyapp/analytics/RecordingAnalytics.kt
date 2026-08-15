package dev.galex.toyapp.analytics

/**
 * The release half of the split, and the reason the recorder can't ship by accident.
 *
 * In a release build there is no recorder to wrap, so this hands the vendor SDK straight back.
 */
fun Analytics.recordingIfDebug(): Analytics = this
