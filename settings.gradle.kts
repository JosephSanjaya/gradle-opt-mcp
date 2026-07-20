enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "GradleOptMCPServer"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.gradle.org/gradle/libs-releases")
    }
}

include(
    ":core",
    ":core:api",
    ":core:impl",
    ":features:dry-run",
    ":features:dry-run:api",
    ":features:dry-run:impl",
    ":features:project-isolation",
    ":features:project-isolation:api",
    ":features:project-isolation:impl",
    ":features:dependency-insight",
    ":features:dependency-insight:api",
    ":features:dependency-insight:impl",
    ":features:configuration-cache",
    ":features:configuration-cache:api",
    ":features:configuration-cache:impl",
    ":features:dependency-verification",
    ":features:dependency-verification:api",
    ":features:dependency-verification:impl",
    ":features:plugin-linter",
    ":features:plugin-linter:api",
    ":features:plugin-linter:impl",
    ":features:build-scan",
    ":features:build-scan:api",
    ":features:build-scan:impl",
    ":features:declarative-schema",
    ":features:declarative-schema:api",
    ":features:declarative-schema:impl",
    ":features:cache-profiler",
    ":features:cache-profiler:api",
    ":features:cache-profiler:impl",
    ":features:parallelism-analyzer",
    ":features:parallelism-analyzer:api",
    ":features:parallelism-analyzer:impl",
    ":features:health",
    ":features:health:api",
    ":features:health:impl",
    ":features:runner",
    ":features:runner:api",
    ":features:runner:impl",
    ":features:test-summary",
    ":features:test-summary:api",
    ":features:test-summary:impl",
    ":server"
)