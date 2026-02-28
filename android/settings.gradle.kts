pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "pcfutbol"

include(":app")
include(":ui")
include(":core:data")
include(":core:season-state")
include(":competition-engine")
include(":match-sim")
include(":manager-economy")
include(":promanager")
include(":test-support")
