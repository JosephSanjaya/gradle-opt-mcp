plugins {
    id("feature.api")
}

base.archivesName = "configuration-cache-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:configuration-cache:api"))
    implementation(project(":features:configuration-cache:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
