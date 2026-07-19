plugins {
    id("tool.mcp.server")
}

group = "com.gradle.optimization.mcp"
version = "1.0.0"

application {
    mainClass.set("com.gradle.optimization.mcp.server.ApplicationKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":features:dry-run"))
    runtimeOnly(project(":features:dry-run:impl"))
    implementation(project(":features:project-isolation"))
    runtimeOnly(project(":features:project-isolation:impl"))
    implementation(project(":features:dependency-insight"))
    runtimeOnly(project(":features:dependency-insight:impl"))
    implementation(project(":features:configuration-cache"))
    runtimeOnly(project(":features:configuration-cache:impl"))
    implementation(project(":features:dependency-verification"))
    runtimeOnly(project(":features:dependency-verification:impl"))
    implementation(project(":features:plugin-linter"))
    runtimeOnly(project(":features:plugin-linter:impl"))
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.gradle.optimization.mcp.server.ApplicationKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
}