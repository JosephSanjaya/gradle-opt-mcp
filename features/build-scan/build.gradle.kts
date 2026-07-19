plugins {
    id("feature.api")
}

base.archivesName = "build-scan-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:build-scan:api"))
    implementation(project(":features:build-scan:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
