rootProject.name = "toy-app"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")
include(":automation-ids")
include(":probe-server")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
