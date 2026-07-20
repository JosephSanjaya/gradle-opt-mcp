plugins {
    id("feature.api")
}

base.archivesName = "declarative-schema-aggregator"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(project(":features:declarative-schema:api"))
    implementation(project(":features:declarative-schema:impl"))
    implementation(libs.findLibrary("koin-core").get())
}
