plugins {
    id("feature.impl")
}

base.archivesName = "parallelism-analyzer-impl"

dependencies {
    implementation(project(":features:parallelism-analyzer:api"))
    implementation(project(":core:api"))
}
