import util.configureKoin

plugins {
    id("tool.jvm")
}

configureKoin()

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("testImplementation", libs.findLibrary("kotlin-test").get())
    add("testImplementation", libs.findLibrary("kotlin-testJunit").get())
}
