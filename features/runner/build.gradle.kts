plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.runner"

dependencies {
    api(projects.features.runner.api)
    implementation(projects.features.runner.impl)
}
