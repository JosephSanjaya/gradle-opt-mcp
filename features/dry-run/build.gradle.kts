plugins {
    id("feature.api")
}

base.archivesName = "dry-run-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:dry-run:api"))
    implementation(project(":features:dry-run:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
