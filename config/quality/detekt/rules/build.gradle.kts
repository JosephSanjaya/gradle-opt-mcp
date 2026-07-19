plugins {
    id("org.jetbrains.kotlin.jvm")
}

plugins.apply("tool.detekt")

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
