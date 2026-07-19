plugins {
    id("feature.impl")
}

base.archivesName = "dry-run-impl"

dependencies {
    implementation(project(":features:dry-run:api"))
    implementation(project(":core:api"))
}
