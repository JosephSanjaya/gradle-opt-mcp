plugins {
    id("feature.api")
}

base.archivesName = "dependency-insight-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:dependency-insight:api"))
    implementation(project(":features:dependency-insight:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
