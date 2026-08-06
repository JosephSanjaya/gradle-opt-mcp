plugins {
    id("feature.impl")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", libs.findLibrary("gradle-tooling-api").get())
    add("implementation", libs.findLibrary("coroutines-core").get())
}
