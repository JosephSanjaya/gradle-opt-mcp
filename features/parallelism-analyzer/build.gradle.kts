plugins {
    id("feature.api")
}

base.archivesName = "parallelism-analyzer-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:parallelism-analyzer:api"))
    implementation(project(":features:parallelism-analyzer:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
