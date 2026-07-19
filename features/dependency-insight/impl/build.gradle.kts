plugins {
    id("feature.impl")
}

base.archivesName = "dependency-insight-impl"

dependencies {
    implementation(project(":features:dependency-insight:api"))
    implementation(project(":core:api"))
}
