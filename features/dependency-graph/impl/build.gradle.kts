plugins {
    id("feature.impl")
}

group = "com.gradle.optimization.mcp.features.dependencygraph"
base.archivesName = "dependency-graph-impl"

dependencies {
    implementation(projects.features.dependencyGraph.api)
    implementation(projects.core.api)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.testJunit)
}
