plugins { id("feature.impl") }
group = "com.gradle.optimization.mcp.features.verification"
base.archivesName = "dependency-verification-impl"
dependencies {
    implementation(projects.features.dependencyVerification.api)
    implementation(projects.core.api)
}
