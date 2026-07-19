package com.gradle.optimization.mcp.features.dryrun.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dryrun.api.DryRunFeatureApi
import com.gradle.optimization.mcp.features.dryrun.api.DryRunRequest
import com.gradle.optimization.mcp.features.dryrun.api.DryRunResult
import com.gradle.optimization.mcp.features.dryrun.api.DryRunTaskNode
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class DryRunFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : DryRunFeatureApi {
    override fun analyzeDryRun(request: DryRunRequest): DryRunResult {
        val targetDir = File(request.projectDir)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        pool.withConnection(targetDir) { connection ->
            val launcher = connection.newBuild()
            val taskList = request.tasks.ifEmpty { listOf("build") }
            launcher.forTasks(*taskList.toTypedArray())
            launcher.withArguments("--dry-run", "--console=plain")
            launcher.setStandardOutput(stdout)
            launcher.setStandardError(stderr)
            launcher.run()
        }

        val rawOutput = stdout.toString(Charsets.UTF_8)
        val parsedTasks = parseDryRunOutput(rawOutput)

        return DryRunResult(
            projectDir = request.projectDir,
            tasksExecuted = parsedTasks,
            rawOutput = rawOutput
        )
    }

    private fun parseDryRunOutput(output: String): List<DryRunTaskNode> {
        val taskNodes = mutableListOf<DryRunTaskNode>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(":")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.isNotEmpty()) {
                    val taskPath = parts[0]
                    val skipped = trimmed.contains("SKIPPED")
                    taskNodes.add(DryRunTaskNode(taskPath = taskPath, skipped = skipped))
                }
            }
        }
        return taskNodes
    }
}
