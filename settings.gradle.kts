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
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vault-ledger"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:workspace")
include(":feature:vault")
include(":feature:transactions")
include(":feature:settings")
