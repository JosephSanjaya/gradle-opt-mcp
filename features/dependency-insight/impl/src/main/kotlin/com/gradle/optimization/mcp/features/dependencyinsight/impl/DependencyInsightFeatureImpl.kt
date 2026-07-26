package com.gradle.optimization.mcp.features.dependencyinsight.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightFeatureApi
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightRequest
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightResult
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class DependencyInsightFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : DependencyInsightFeatureApi {
    override fun getDependencyInsight(request: DependencyInsightRequest): DependencyInsightResult {
        require(request.projectDir.isNotBlank()) { "projectDir must not be blank" }
        require(request.configuration.isNotBlank()) { "configuration must not be blank" }
        require(request.dependency.isNotBlank()) { "dependency must not be blank" }

        val targetDir = File(request.projectDir)
        require(targetDir.exists() && targetDir.isDirectory) {
            "projectDir does not exist or is not a directory: ${request.projectDir}"
        }

        val modulePath = normalizeModulePath(request.modulePath)
        val configuration = request.configuration.trim()
        val dependency = request.dependency.trim()
        val taskPath = insightTaskPath(modulePath)

        val initScript = File.createTempFile("mcp-dependency-insight", ".gradle")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var toolingError: Throwable? = null

        try {
            initScript.writeText(buildInitScript(modulePath, configuration, dependency))

            runCatching {
                pool.withConnection(targetDir) { connection ->
                    val launcher = connection.newBuild()
                    launcher.forTasks(taskPath)
                    launcher.withArguments(
                        "--init-script",
                        initScript.absolutePath,
                        "--no-configuration-cache",
                        "--console=plain"
                    )
                    launcher.setStandardOutput(stdout)
                    launcher.setStandardError(stderr)
                    launcher.run()
                }
            }.onFailure { error ->
                toolingError = error
            }
        } finally {
            initScript.delete()
        }

        val rawOutput = stdout.toString(Charsets.UTF_8)
        val rawErr = stderr.toString(Charsets.UTF_8)
        val diagnostics = buildString {
            append(rawOutput)
            if (rawErr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(rawErr)
            }
        }

        val parsed = DependencyInsightParser.parse(
            raw = rawOutput,
            maxPaths = request.maxPaths,
            maxReasons = request.maxReasons
        )

        val failureReason = resolveFailureReason(
            toolingError = toolingError,
            diagnostics = diagnostics,
            parsed = parsed
        )

        val found = toolingError == null && parsed.found
        return DependencyInsightResult(
            projectDir = request.projectDir,
            modulePath = modulePath,
            configuration = configuration,
            dependency = dependency,
            found = found,
            selectedVersion = parsed.selectedVersion.takeIf { found },
            reasons = if (found) parsed.reasons else emptyList(),
            paths = if (found) parsed.paths else emptyList(),
            failureReason = failureReason
        )
    }

    private fun resolveFailureReason(
        toolingError: Throwable?,
        diagnostics: String,
        parsed: ParsedDependencyInsight
    ): String? {
        DependencyInsightParser.extractBuildFailureReason(diagnostics)?.let { return it }
        if (toolingError != null) {
            usefulCauseMessage(toolingError)?.let { return it }
            return toolingError.message?.takeIf { it.isNotBlank() } ?: toolingError.toString()
        }
        return if (!parsed.found) parsed.notFoundMessage else null
    }

    private fun usefulCauseMessage(error: Throwable): String? =
        generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf { msg -> msg.isNotBlank() } }
            .firstOrNull { msg ->
                msg.startsWith("Unknown configuration") ||
                    msg.contains("Unknown configuration '") ||
                    msg.contains("Task ") && msg.contains("not found") ||
                    msg.contains("configuration '") && msg.contains("not found") ||
                    !msg.startsWith("Could not execute build using connection")
            }

    private fun buildInitScript(modulePath: String, configuration: String, dependency: String): String {
        val path = escapeGroovy(modulePath)
        val config = escapeGroovy(configuration)
        val dep = escapeGroovy(dependency)
        return """
            def targetPath = "$path"
            def configName = "$config"
            def depSpec = "$dep"

            allprojects {
                afterEvaluate { p ->
                    if (p.path != targetPath) {
                        return
                    }
                    def cfg = p.configurations.findByName(configName)
                    if (cfg == null) {
                        throw new GradleException(
                            "Unknown configuration '" + configName + "' on project '" + p.path + "'"
                        )
                    }
                    p.tasks.register(
                        "mcpDependencyInsight",
                        org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
                    ) {
                        setConfiguration(cfg)
                        setDependencySpec(depSpec)
                        showingAllVariants.convention(false)
                    }
                }
            }
        """.trimIndent()
    }

    companion object {
        internal fun normalizeModulePath(raw: String): String {
            val trimmed = raw.trim().ifEmpty { DependencyInsightRequest.DEFAULT_MODULE_PATH }
            return if (trimmed == ":" || trimmed.startsWith(":")) trimmed else ":$trimmed"
        }

        internal fun insightTaskPath(modulePath: String): String =
            if (modulePath == ":") ":mcpDependencyInsight" else "$modulePath:mcpDependencyInsight"

        private fun escapeGroovy(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
