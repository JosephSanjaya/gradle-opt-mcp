plugins { id("feature.impl") }

group = "com.gradle.optimization.mcp.features.testsummary"
base.archivesName = "test-summary-impl"

dependencies {
    implementation(projects.features.testSummary.api)
    implementation(projects.core.api)
    testImplementation(libs.kotlin.testJunit)
}
