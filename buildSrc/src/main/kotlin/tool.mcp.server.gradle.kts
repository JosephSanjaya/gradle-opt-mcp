import util.configureKoin

plugins {
    id("tool.jvm")
    application
}

configureKoin()

val libs = versionCatalogs.find("libs")

dependencies {
    implementation(libs.flatMap { it.findLibrary("mcp-sdk") }.get())
    implementation(libs.flatMap { it.findLibrary("logback") }.get())
    implementation(libs.flatMap { it.findLibrary("kotlinx-coroutines-core") }.get())
}
