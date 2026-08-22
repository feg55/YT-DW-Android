pluginManagement {
    repositories {
        System.getenv("GRADLE_PLUGIN_MIRROR")?.takeIf(String::isNotBlank)?.let { mirror ->
            maven { url = uri(mirror) }
        }
        System.getenv("GOOGLE_MAVEN_MIRROR")?.takeIf(String::isNotBlank)?.let { mirror ->
            maven { url = uri(mirror) }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        System.getenv("GOOGLE_MAVEN_MIRROR")?.takeIf(String::isNotBlank)?.let { mirror ->
            maven { url = uri(mirror) }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "YT-DW Android"
include(":app")
