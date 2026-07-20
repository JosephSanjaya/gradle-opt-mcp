plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.testsummary"

dependencies {
    api(projects.features.testSummary.api)
    implementation(projects.features.testSummary.impl)
}
