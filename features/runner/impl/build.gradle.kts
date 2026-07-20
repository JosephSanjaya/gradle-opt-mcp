plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.runner"
base.archivesName = "runner-impl"

dependencies {
    implementation(projects.features.runner.api)
    implementation(projects.core.api)
}
