package com.gradle.optimization.mcp.features.dryrun.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dryrun.api.DryRunCount
import com.gradle.optimization.mcp.features.dryrun.api.DryRunFeatureApi
import com.gradle.optimization.mcp.features.dryrun.api.DryRunRequest
import com.gradle.optimization.mcp.features.dryrun.api.DryRunResult
import com.gradle.optimization.mcp.features.dryrun.api.DryRunTaskNode
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

private const val SAMPLE_TASK_LIMIT = 25
private const val FAILURE_REASON_MAX_CHARS = 1200
private const val FAILURE_REASON_MAX_LINES = 12

@Single
class DryRunFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : DryRunFeatureApi {
    override fun analyzeDryRun(request: DryRunRequest): DryRunResult {
        val targetDir = File(request.projectDir)
        require(targetDir.isDirectory) {
            "Project directory does not exist or is not a directory: ${request.projectDir}"
        }

        val taskList = request.tasks.ifEmpty { listOf("build") }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var exceptionMessage: String? = null

        val success = runCatching {
            pool.withConnection(targetDir) { connection ->
                connection.newBuild()
                    .forTasks(*taskList.toTypedArray())
                    .withArguments("--dry-run", "--console=plain")
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .run()
            }
            true
        }.getOrElse { error ->
            exceptionMessage = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            false
        }

        val combinedOutput = buildString {
            append(stdout.toString(Charsets.UTF_8))
            append('\n')
            append(stderr.toString(Charsets.UTF_8))
            if (!exceptionMessage.isNullOrBlank() && !contains(exceptionMessage!!)) {
                append('\n')
                append(exceptionMessage)
            }
        }

        val taskPaths = DryRunOutputParser.parseTaskPaths(combinedOutput)
        val byModule = DryRunOutputParser.countByModule(taskPaths)
        val byLeafType = DryRunOutputParser.countByLeafType(taskPaths)
        val sampleTasks = taskPaths.take(SAMPLE_TASK_LIMIT).map { path ->
            DryRunTaskNode(taskPath = path, status = DryRunTaskNode.STATUS_SCHEDULED)
        }
        val failureReason = if (success) {
            null
        } else {
            DryRunOutputParser.extractFailureReason(combinedOutput) ?: exceptionMessage
        }

        val summary = if (success) {
            "Dry-run planned ${taskPaths.size} scheduled task(s) for ${taskList.joinToString(", ")}."
        } else {
            buildString {
                append("Dry-run failed for ${taskList.joinToString(", ")}.")
                if (!failureReason.isNullOrBlank()) {
                    append(" Reason: ${failureReason.lineSequence().first()}")
                }
            }
        }

        return DryRunResult(
            projectDir = request.projectDir,
            success = success,
            requestedTasks = taskList,
            taskCount = taskPaths.size,
            byModule = byModule,
            byLeafType = byLeafType,
            sampleTasks = sampleTasks,
            failureReason = failureReason,
            summary = summary
        )
    }
}

internal object DryRunOutputParser {
    private val taskLineRegex = Regex("""^(:\S+)(?:\s+\S+)*\s*$""")

    fun parseTaskPaths(output: String): List<String> {
        val paths = mutableListOf<String>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith(":")) return@forEach
            val match = taskLineRegex.matchEntire(trimmed) ?: return@forEach
            paths.add(match.groupValues[1])
        }
        return paths
    }

    fun countByModule(taskPaths: List<String>): List<DryRunCount> =
        taskPaths
            .groupingBy { modulePath(it) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { DryRunCount(name = it.key, count = it.value) }

    fun countByLeafType(taskPaths: List<String>): List<DryRunCount> =
        taskPaths
            .groupingBy { leafType(it) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { DryRunCount(name = it.key, count = it.value) }

    fun extractFailureReason(output: String): String? {
        val marker = "* What went wrong:"
        val start = output.indexOf(marker)
        if (start < 0) return null

        val after = output.substring(start + marker.length).trimStart()
        val endMarkers = listOf("* Try:", "* Exception is:", "BUILD FAILED")
        val end = endMarkers
            .map { after.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: after.length.coerceAtMost(FAILURE_REASON_MAX_CHARS)

        return after.take(end)
            .trim()
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .take(FAILURE_REASON_MAX_LINES)
            .joinToString("\n")
            .ifBlank { null }
    }

    fun modulePath(taskPath: String): String {
        val lastColon = taskPath.lastIndexOf(':')
        return if (lastColon > 0) taskPath.substring(0, lastColon) else ":"
    }

    fun leafType(taskPath: String): String {
        val lastColon = taskPath.lastIndexOf(':')
        return if (lastColon >= 0 && lastColon < taskPath.lastIndex) {
            taskPath.substring(lastColon + 1)
        } else {
            taskPath
        }
    }
}
