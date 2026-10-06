pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Jev for WhatsApp: a separate Gradle build. It shares no code with the
// Chinese app in the repository root and is built from this directory.
rootProject.name = "jev-overseas"
include(":core")
include(":a11y")
include(":assistant")
include(":probe")
