package com.gradle.optimization.mcp.features.configcache.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditResult
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheInputViolation
import com.gradle.optimization.mcp.features.configcache.api.ConfigurationCacheFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class ConfigurationCacheFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ConfigurationCacheFeatureApi {
    override fun auditConfigurationCacheInputs(request: ConfigCacheAuditRequest): ConfigCacheAuditResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Target directory does not exist: ${request.projectDir}" }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        runCatching {
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                val tasksToRun = request.tasks.ifEmpty { listOf("help") }
                launcher.forTasks(*tasksToRun.toTypedArray())
                launcher.withArguments(
                    "-Dorg.gradle.configuration-cache=true",
                    "--console=plain"
                )
                launcher.setStandardOutput(stdout)
                launcher.setStandardError(stderr)
                launcher.run()
            }
        }

        val rawOutput = stdout.toString(Charsets.UTF_8) + "\n" + stderr.toString(Charsets.UTF_8)
        val cacheHit = rawOutput.contains("Configuration cache entry reused", ignoreCase = true)
        val violations = parseInputViolations(rawOutput)
        val reportPath = findHtmlReport(targetDir)

        val summary = when {
            cacheHit -> "Configuration cache hit. Entry reused successfully."
            violations.isNotEmpty() ->
                "Found ${violations.size} configuration input violation(s) invalidating configuration cache."
            else -> "Configuration cache calculated with no detected input violations."
        }

        return ConfigCacheAuditResult(
            projectDir = request.projectDir,
            cacheHit = cacheHit,
            inputsAudited = violations,
            htmlReportPath = reportPath,
            summary = summary
        )
    }

    internal fun parseInputViolations(output: String): List<ConfigCacheInputViolation> {
        val violations = mutableListOf<ConfigCacheInputViolation>()

        output.lineSequence().forEach { line ->
            val isEnv = line.contains("System.getenv", ignoreCase = true) ||
                line.contains("environment variable", ignoreCase = true)
            val isProp = line.contains("System.getProperty", ignoreCase = true) ||
                line.contains("system property", ignoreCase = true)
            val isList = line.contains("listFiles", ignoreCase = true) ||
                line.contains("directory content", ignoreCase = true)
            val isRead = line.contains("readText", ignoreCase = true) ||
                line.contains("file content", ignoreCase = true)

            when {
                isEnv -> {
                    violations.add(
                        ConfigCacheInputViolation(
                            inputName = line.trim(),
                            inputType = "ENVIRONMENT_VARIABLE",
                            antiPattern = "Direct System.getenv() access",
                            recommendedRefactoring = "providers.environmentVariable(\"KEY\")"
                        )
                    )
                }
                isProp -> {
                    violations.add(
                        ConfigCacheInputViolation(
                            inputName = line.trim(),
                            inputType = "SYSTEM_PROPERTY",
                            antiPattern = "Direct System.getProperty() access",
                            recommendedRefactoring = "providers.systemProperty(\"prop\")"
                        )
                    )
                }
                isList -> {
                    violations.add(
                        ConfigCacheInputViolation(
                            inputName = line.trim(),
                            inputType = "DIRECTORY_LISTING",
                            antiPattern = "File.listFiles() or directory listing",
                            recommendedRefactoring = "ObjectFactory.fileCollection()"
                        )
                    )
                }
                isRead -> {
                    violations.add(
                        ConfigCacheInputViolation(
                            inputName = line.trim(),
                            inputType = "FILE_READ",
                            antiPattern = "Direct File.readText() access",
                            recommendedRefactoring = "providers.fileContents(...)"
                        )
                    )
                }
            }
        }

        return violations.distinctBy { it.inputName }
    }

    private fun findHtmlReport(projectDir: File): String? {
        val possibleReportDirs = listOf(
            File(projectDir, "build/reports/configuration-cache"),
            File(projectDir, ".gradle/configuration-cache")
        )
        for (dir in possibleReportDirs) {
            if (dir.exists() && dir.isDirectory) {
                val reportFile = dir.walkTopDown().firstOrNull { it.name.endsWith(".html") }
                if (reportFile != null) {
                    return reportFile.absolutePath
                }
            }
        }
        return null
    }
}
