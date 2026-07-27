package dev.galex.toyapp.ui

/**
 * Where we are in the app, as a breadcrumb like "Toys > ToyDetail".
 *
 * The probe reads this through a hook, so a flow can assert on the destination instead of on
 * visible copy. Copy changes with every translation, a breadcrumb does not.
 */
object NavBridge {
    @Volatile
    var breadcrumb: String = ""
        private set

    fun set(value: String) {
        breadcrumb = value
    }
}
