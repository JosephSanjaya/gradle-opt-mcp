plugins {
    id("feature.api")
}

base.archivesName = "cache-profiler-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:cache-profiler:api"))
    implementation(project(":features:cache-profiler:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
