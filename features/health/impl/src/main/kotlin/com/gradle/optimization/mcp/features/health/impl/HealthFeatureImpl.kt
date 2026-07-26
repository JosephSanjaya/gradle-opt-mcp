package com.gradle.optimization.mcp.features.health.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.health.api.GradleHealthRequest
import com.gradle.optimization.mcp.features.health.api.GradleHealthResult
import com.gradle.optimization.mcp.features.health.api.HealthFeatureApi
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.Properties

private const val SUBPROJECT_NAMES_LIMIT = 50

@Single
class HealthFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : HealthFeatureApi {
    override fun checkHealth(request: GradleHealthRequest): GradleHealthResult {
        require(request.projectDir.isNotBlank()) { "projectDir is required" }
        val targetDir = File(request.projectDir).canonicalFile
        require(targetDir.isDirectory) { "Project directory does not exist: ${request.projectDir}" }
        require(looksLikeGradleProject(targetDir)) {
            "Not a Gradle project (missing settings/build script or wrapper): ${targetDir.path}"
        }

        val (env, rootProject) = pool.withConnection(targetDir) { connection ->
            Pair(
                connection.getModel(BuildEnvironment::class.java),
                connection.getModel(GradleProject::class.java)
            )
        }

        val gradleVersion = env.gradle.gradleVersion
        val javaHomeFile = env.java.javaHome
        val javaHome = javaHomeFile.absolutePath
        val (javaVersion, javaVendor) = readJavaRelease(javaHomeFile)

        val allSubprojectPaths = collectAllSubprojectPaths(rootProject)
        val subprojectsTruncated = allSubprojectPaths.size > SUBPROJECT_NAMES_LIMIT
        val subprojectNames = allSubprojectPaths.take(SUBPROJECT_NAMES_LIMIT)

        val wrapperVersion = parseWrapperVersion(targetDir)
        val buildSrcPresent = File(targetDir, "buildSrc").isDirectory
        val props = readGradleProperties(targetDir)
        val configurationCacheEnabled = props.isTrue("org.gradle.configuration-cache")
        val cachingEnabled = props.isTrue("org.gradle.caching")
        val parallelEnabled = props.isTrue("org.gradle.parallel")
        val hasSettings = hasSettingsFile(targetDir)

        val gaps = buildGaps(
            wrapperVersion = wrapperVersion,
            gradleVersion = gradleVersion,
            hasSettings = hasSettings,
            configurationCacheEnabled = configurationCacheEnabled,
            cachingEnabled = cachingEnabled,
            parallelEnabled = parallelEnabled
        )

        val osName = System.getProperty("os.name", "Unknown")
        val osArch = System.getProperty("os.arch", "Unknown")

        val summary = buildString {
            append("Gradle Health Check for '${rootProject.name}' at ${targetDir.path}: ")
            append("Gradle $gradleVersion, Java $javaVersion ($javaVendor), ")
            append("${allSubprojectPaths.size} subproject(s)")
            if (subprojectsTruncated) append(" (listing first $SUBPROJECT_NAMES_LIMIT)")
            append(". ")
            if (wrapperVersion != null) append("Wrapper: $wrapperVersion. ")
            if (buildSrcPresent) append("buildSrc detected. ")
            if (gaps.isEmpty()) {
                append("No gaps.")
            } else {
                append("${gaps.size} gap(s).")
            }
        }.trim()

        return GradleHealthResult(
            projectDir = targetDir.path,
            gradleVersion = gradleVersion,
            javaVersion = javaVersion,
            javaVendor = javaVendor,
            javaHome = javaHome,
            osName = osName,
            osArch = osArch,
            rootProjectName = rootProject.name,
            subprojectCount = allSubprojectPaths.size,
            subprojectNames = subprojectNames,
            subprojectsTruncated = subprojectsTruncated,
            wrapperVersion = wrapperVersion,
            buildSrcPresent = buildSrcPresent,
            configurationCacheEnabled = configurationCacheEnabled,
            cachingEnabled = cachingEnabled,
            parallelEnabled = parallelEnabled,
            gaps = gaps,
            summary = summary
        )
    }

    private fun looksLikeGradleProject(projectDir: File): Boolean =
        hasSettingsFile(projectDir) ||
            File(projectDir, "build.gradle.kts").isFile ||
            File(projectDir, "build.gradle").isFile ||
            File(projectDir, "gradle/wrapper/gradle-wrapper.properties").isFile

    private fun hasSettingsFile(projectDir: File): Boolean =
        File(projectDir, "settings.gradle.kts").isFile || File(projectDir, "settings.gradle").isFile

    private fun collectAllSubprojectPaths(project: GradleProject): List<String> {
        val paths = mutableListOf<String>()
        for (child in project.children) {
            paths.add(child.path)
            paths.addAll(collectAllSubprojectPaths(child))
        }
        return paths
    }

    private fun parseWrapperVersion(projectDir: File): String? {
        val wrapperPropsFile = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperPropsFile.isFile) return null
        return runCatching {
            val props = Properties()
            wrapperPropsFile.inputStream().use { props.load(it) }
            val distUrl = props.getProperty("distributionUrl") ?: return null
            val regex =
                """gradle-(\d+\.\d+(?:\.\d+)?(?:-(?:rc|milestone|alpha|beta)-\d+)?)(?:-(?:bin|all|src))?\.zip"""
                    .toRegex()
            regex.find(distUrl)?.groupValues?.get(1)
        }.getOrNull()
    }

    private fun readGradleProperties(projectDir: File): Properties {
        val props = Properties()
        val file = File(projectDir, "gradle.properties")
        if (file.isFile) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun Properties.isTrue(key: String): Boolean =
        getProperty(key)?.trim().equals("true", ignoreCase = true)

    private fun readJavaRelease(javaHome: File): Pair<String, String> {
        val release = File(javaHome, "release")
        if (!release.isFile) return "Unknown" to "Unknown"
        return runCatching {
            val props = Properties()
            release.inputStream().use { props.load(it) }
            val version = props.getProperty("JAVA_VERSION").orEmpty().trim('"').ifBlank { "Unknown" }
            val vendor = props.getProperty("IMPLEMENTOR").orEmpty().trim('"').ifBlank { "Unknown" }
            version to vendor
        }.getOrDefault("Unknown" to "Unknown")
    }

    private fun buildGaps(
        wrapperVersion: String?,
        gradleVersion: String,
        hasSettings: Boolean,
        configurationCacheEnabled: Boolean,
        cachingEnabled: Boolean,
        parallelEnabled: Boolean
    ): List<String> = buildList {
        when {
            wrapperVersion == null -> add("Gradle Wrapper missing")
            wrapperVersion != gradleVersion ->
                add("Wrapper version ($wrapperVersion) mismatches runtime Gradle ($gradleVersion)")
        }
        if (!hasSettings) add("No settings.gradle(.kts) found")
        if (!configurationCacheEnabled) add("Configuration Cache off (org.gradle.configuration-cache!=true)")
        if (!cachingEnabled) add("Build Cache off (org.gradle.caching!=true)")
        if (!parallelEnabled) add("Parallel execution off (org.gradle.parallel!=true)")
    }
}
