plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.health"
base.archivesName = "health-impl"

dependencies {
    implementation(projects.features.health.api)
    implementation(projects.core.api)
}
