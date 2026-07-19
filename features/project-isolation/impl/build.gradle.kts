plugins {
    id("feature.impl")
}

base.archivesName = "project-isolation-impl"

dependencies {
    implementation(project(":features:project-isolation:api"))
    implementation(project(":core:api"))
}
