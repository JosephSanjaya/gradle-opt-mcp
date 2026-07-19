plugins {
    id("feature.api")
}

base.archivesName = "project-isolation-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:project-isolation:api"))
    implementation(project(":features:project-isolation:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
