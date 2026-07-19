plugins {
    id("tool.jvm")
}

apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
}
