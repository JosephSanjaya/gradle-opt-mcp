plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("detektPlugins", libs.findLibrary("detekt-formatting").get())
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    config.setFrom(rootProject.file("config/quality/detekt/detekt-config.yml"))
    val baselineFile = rootProject.file("config/quality/detekt/baseline/${project.name}-baseline.xml")
    if (baselineFile.exists()) {
        baseline = baselineFile
    }
}
