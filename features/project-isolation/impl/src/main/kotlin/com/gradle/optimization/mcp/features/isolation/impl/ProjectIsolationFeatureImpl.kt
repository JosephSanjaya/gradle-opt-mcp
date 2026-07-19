package com.gradle.optimization.mcp.features.isolation.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckRequest
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckResult
import com.gradle.optimization.mcp.features.isolation.api.IsolationViolation
import com.gradle.optimization.mcp.features.isolation.api.ProjectIsolationFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class ProjectIsolationFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ProjectIsolationFeatureApi {
    override fun checkProjectIsolation(request: IsolationCheckRequest): IsolationCheckResult {
        val targetDir = File(request.projectDir)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        runCatching {
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks("help")
                launcher.withArguments(
                    "-Dorg.gradle.unsafe.isolated-projects=true",
                    "--console=plain"
                )
                launcher.setStandardOutput(stdout)
                launcher.setStandardError(stderr)
                launcher.run()
            }
        }

        val rawOutput = stdout.toString(Charsets.UTF_8) + "\n" + stderr.toString(Charsets.UTF_8)
        val violations = parseViolations(rawOutput)

        return IsolationCheckResult(
            projectDir = request.projectDir,
            isIsolated = violations.isEmpty(),
            violations = violations
        )
    }

    private fun parseViolations(output: String): List<IsolationViolation> {
        val violations = mutableListOf<IsolationViolation>()
        output.lineSequence().forEach { line ->
            val hasIsolationMatch = line.contains("Project isolation", ignoreCase = true) ||
                line.contains("Project Access", ignoreCase = true)
            if (hasIsolationMatch) {
                violations.add(
                    IsolationViolation(
                        message = line.trim(),
                        violationType = "PROJECT_ACCESS"
                    )
                )
            }
        }
        return violations
    }
}
