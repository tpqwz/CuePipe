pluginManagement {
    repositories {
        // Google's Maven host was answering 404, so Android groups come from this mirror.
        exclusiveContent {
            forRepository {
                maven(url = "https://maven.aliyun.com/repository/google")
            }
            filter {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.mlkit(\\..*)?")
                includeGroupByRegex("com\\.google\\.firebase(\\..*)?")
                includeGroupByRegex("com\\.google\\.android\\.datatransport(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google's Maven host was answering 404, so Android groups come from this mirror.
        exclusiveContent {
            forRepository {
                maven(url = "https://maven.aliyun.com/repository/google")
            }
            filter {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.mlkit(\\..*)?")
                includeGroupByRegex("com\\.google\\.firebase(\\..*)?")
                includeGroupByRegex("com\\.google\\.android\\.datatransport(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "cuepipe"
include(":app")
