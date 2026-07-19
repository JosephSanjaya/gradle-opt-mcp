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

        val initScript = File.createTempFile("mcp-dependency-insight", ".gradle")
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            initScript.writeText(
                """
                allprojects {
                    afterEvaluate { p ->
                        def targetCfg = p.configurations.findByName("${request.configuration}") ?:
                            (p.configurations.isEmpty() ? null : p.configurations.first())
                        if (targetCfg != null) {
                            p.tasks.register("mcpDependencyInsight", org.gradle.api.tasks.diagnostics.DependencyInsightReportTask) {
                                setConfiguration(targetCfg)
                                setDependencySpec("${request.dependency}")
                                showingAllVariants.convention(false)
                            }
                        }
                    }
                }
                """.trimIndent()
            )

            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks("mcpDependencyInsight")
                launcher.withArguments("--init-script", initScript.absolutePath, "-q")
                launcher.setStandardOutput(stdout)
                launcher.setStandardError(stderr)
                launcher.run()
            }
        } finally {
            initScript.delete()
        }

        val rawOutput = stdout.toString(Charsets.UTF_8).ifBlank { stderr.toString(Charsets.UTF_8) }

        return DependencyInsightResult(
            projectDir = request.projectDir,
            configuration = request.configuration,
            dependency = request.dependency,
            insightOutput = rawOutput
        )
    }
}
