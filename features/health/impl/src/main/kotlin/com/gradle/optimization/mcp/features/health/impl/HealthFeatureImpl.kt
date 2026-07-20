package com.gradle.optimization.mcp.features.health.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
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

@Single
class HealthFeatureImpl(
    @Provided private val pool: GradleConnectionPool,
    @Provided private val config: GradleConfig
) : HealthFeatureApi {
    override fun checkHealth(request: GradleHealthRequest): GradleHealthResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists()) { "Project directory does not exist: $targetDirPath" }

        val (gradleVersion, javaHome) = runCatching {
            pool.withConnection(targetDir) { connection ->
                val env = connection.getModel(BuildEnvironment::class.java)
                Pair(env.gradle.gradleVersion, env.java.javaHome.absolutePath)
            }
        }.getOrElse {
            Pair(System.getProperty("gradle.version", "Unknown"), System.getProperty("java.home", "Unknown"))
        }

        val (rootProjectName, subprojectNames) = runCatching {
            pool.withConnection(targetDir) { connection ->
                val rootProject = connection.getModel(GradleProject::class.java)
                val names = rootProject.children.map { it.name }
                Pair(rootProject.name, names)
            }
        }.getOrElse {
            Pair(targetDir.name, emptyList())
        }

        val wrapperVersion = parseWrapperVersion(targetDir)
        val buildSrcPresent = File(targetDir, "buildSrc").exists()
        val hasCcConfig = File(targetDir, "gradle.properties").takeIf { it.exists() }
            ?.readText()
            ?.contains("org.gradle.configuration-cache=true") == true

        val javaVersion = System.getProperty("java.version", "Unknown")
        val javaVendor = System.getProperty("java.vendor", "Unknown")
        val osName = System.getProperty("os.name", "Unknown")
        val osArch = System.getProperty("os.arch", "Unknown")

        val summary = buildString {
            append("Gradle Health Check for '$rootProjectName': ")
            append("Gradle $gradleVersion, Java $javaVersion ($javaVendor), ")
            append("${subprojectNames.size} subproject(s). ")
            if (wrapperVersion != null) append("Wrapper: $wrapperVersion. ")
            if (buildSrcPresent) append("buildSrc detected. ")
            if (hasCcConfig) append("Configuration Cache enabled.")
        }.trim()

        return GradleHealthResult(
            gradleVersion = gradleVersion,
            javaVersion = javaVersion,
            javaVendor = javaVendor,
            javaHome = javaHome,
            osName = osName,
            osArch = osArch,
            rootProjectName = rootProjectName,
            subprojectCount = subprojectNames.size,
            subprojectNames = subprojectNames,
            wrapperVersion = wrapperVersion,
            buildSrcPresent = buildSrcPresent,
            configurationCacheConfigFile = hasCcConfig,
            summary = summary
        )
    }

    private fun parseWrapperVersion(projectDir: File): String? {
        val wrapperPropsFile = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperPropsFile.exists()) return null
        return runCatching {
            val props = Properties()
            wrapperPropsFile.inputStream().use { props.load(it) }
            val distUrl = props.getProperty("distributionUrl") ?: return null
            val regex = """gradle-([0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-\w+)?)""".toRegex()
            regex.find(distUrl)?.groupValues?.get(1)
        }.getOrNull()
    }
}
