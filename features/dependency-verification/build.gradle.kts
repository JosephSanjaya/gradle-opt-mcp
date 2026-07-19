plugins { id("feature.impl") }
group = "com.gradle.optimization.mcp.features.verification"
dependencies {
    api(projects.features.dependencyVerification.api)
    implementation(projects.features.dependencyVerification.impl)
}
