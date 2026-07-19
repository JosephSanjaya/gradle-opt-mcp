plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.koin.compiler)
    implementation(libs.gradle.detekt)
    implementation(libs.detekt.formatting)
    compileOnly(libs.detekt.formatting)
}
