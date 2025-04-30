// settings.gradle.kts

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
        // Add the Mapbox Maven repository here:
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            // NO CREDENTIALS BLOCK NEEDED HERE FOR PUBLIC SDK ACCESS
        }
    }
}

rootProject.name = "Roots_D01"
include(":app")