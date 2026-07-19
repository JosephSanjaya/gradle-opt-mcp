plugins {
    id("feature.api")
}

base.archivesName = "core-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":core:api"))
    implementation(project(":core:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
