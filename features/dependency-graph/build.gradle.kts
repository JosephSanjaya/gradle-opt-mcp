plugins {
    id("feature.impl")
}

group = "com.gradle.optimization.mcp.features.dependencygraph"

dependencies {
    api(projects.features.dependencyGraph.api)
    implementation(projects.features.dependencyGraph.impl)
}
