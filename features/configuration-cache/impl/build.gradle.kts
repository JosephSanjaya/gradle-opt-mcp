plugins {
    id("feature.impl")
}

base.archivesName = "configuration-cache-impl"

dependencies {
    implementation(project(":features:configuration-cache:api"))
    implementation(project(":core:api"))
}
