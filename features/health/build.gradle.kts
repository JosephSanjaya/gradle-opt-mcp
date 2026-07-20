plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.health"

dependencies {
    api(projects.features.health.api)
    implementation(projects.features.health.impl)
}
