plugins {
    id("feature.impl")
}

base.archivesName = "declarative-schema-impl"

dependencies {
    implementation(project(":features:declarative-schema:api"))
    implementation(project(":core:api"))
}
