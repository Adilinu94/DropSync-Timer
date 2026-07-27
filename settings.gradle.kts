// DropSync Projektstruktur gemaess Bauplan Abschnitt 3.2.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "DropSync"

// :app
include(":app")

// :core
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":core:testing")

// :data
include(":data:audio") // ADR-0005
include(":data:library")
include(":data:playback")
include(":data:timer")
include(":data:workout")

// :domain
include(":domain:audio") // ADR-0005
include(":domain:library") // ADR-0003
include(":domain:playback") // ADR-0004
include(":domain:timer")
include(":domain:workout")

// :feature
include(":feature:library")
include(":feature:player")
include(":feature:timer")
include(":feature:workout")
include(":feature:settings")

// :baselineprofile wird gemaess ADR-0001 erst in Schritt 13 angelegt.
