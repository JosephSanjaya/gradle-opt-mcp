plugins {
    id("feature.impl")
}

base.archivesName = "build-scan-impl"

dependencies {
    implementation(project(":features:build-scan:api"))
    implementation(project(":core:api"))
}
