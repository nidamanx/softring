// SPDX-License-Identifier: AGPL-3.0-or-later

// Google's repository is asked only for the groups it publishes (Android, AndroidX,
// Google), as in Android Studio's templates: the other artifacts go straight to Maven
// Central and the Gradle Plugin Portal
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
    // Only the repositories declared here may be used by modules
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "SoftRing"
include(":app")
