rootProject.name = "gradle-opt-mcp-workspace-build-logic"

pluginManagement {
    includeBuild("../sjy")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../sjy/gradle/libs.versions.toml"))
        }
    }
}

include(":convention")
