plugins {
    id("core")
}

base.archivesName = "core-impl"

dependencies {
    implementation(project(":core:api"))
}
