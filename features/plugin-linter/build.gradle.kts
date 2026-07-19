plugins { id("feature.impl") }
group = "com.gradle.optimization.mcp.features.linter"
dependencies {
    api(projects.features.pluginLinter.api)
    implementation(projects.features.pluginLinter.impl)
}
