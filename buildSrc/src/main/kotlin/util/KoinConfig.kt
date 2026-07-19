package util

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

fun Project.configureKoin() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    pluginManager.apply("io.insert-koin.compiler.plugin")

    dependencies {
        add("implementation", libs.findLibrary("koin-core").get())
        add("implementation", libs.findLibrary("koin-annotations").get())
    }
}
