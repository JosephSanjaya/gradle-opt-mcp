plugins { id("feature.impl") }
group = "com.gradle.optimization.mcp.features.linter"
base.archivesName = "plugin-linter-impl"
dependencies {
    implementation(projects.features.pluginLinter.api)
    implementation(projects.core.api)
}
