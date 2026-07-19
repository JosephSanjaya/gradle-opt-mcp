plugins {
    id("feature.api")
}

base.archivesName = "core-api"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(libs.findLibrary("gradle-tooling-api").get())
}
