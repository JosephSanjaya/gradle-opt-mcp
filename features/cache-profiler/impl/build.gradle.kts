plugins {
    id("feature.impl")
}

base.archivesName = "cache-profiler-impl"

dependencies {
    implementation(project(":features:cache-profiler:api"))
    implementation(project(":core:api"))
    implementation(project(":features:configuration-cache:api"))
    implementation(project(":features:configuration-cache:impl"))
}
