package com.gradle.optimization.mcp.features.runner.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.runner.api.GradleRunLogRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunLogResult
import com.gradle.optimization.mcp.features.runner.api.GradleRunRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunResult
import com.gradle.optimization.mcp.features.runner.api.RunnerFeatureApi
import com.gradle.optimization.mcp.features.runner.api.TaskOutcome
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class RunnerFeatureImpl(
    @Provided private val pool: GradleConnectionPool,
    @Provided private val config: GradleConfig
) : RunnerFeatureApi {
    override fun runBuild(request: GradleRunRequest): GradleRunResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists()) { "Project directory does not exist: $targetDirPath" }

        val taskList = request.tasks.ifEmpty { listOf("build") }
        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        val taskOutcomes = mutableListOf<TaskOutcome>()
        val startTime = System.currentTimeMillis()
        var exceptionMessage: String? = null

        val success = runCatching {
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                    .forTasks(*taskList.toTypedArray())
                    .setStandardOutput(stdoutStream)
                    .setStandardError(stderrStream)
                    .addProgressListener(
                        ProgressListener { event: ProgressEvent ->
                            if (event is TaskFinishEvent) {
                                taskOutcomes.add(
                                    TaskOutcome(event.descriptor.taskPath, mapOutcome(event.result))
                                )
                            }
                        },
                        setOf(OperationType.TASK)
                    )

                if (request.arguments.isNotEmpty()) {
                    launcher.withArguments(request.arguments)
                }

                launcher.run()
            }
            true
        }.getOrElse { error ->
            exceptionMessage = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            false
        }

        val executionTimeMs = System.currentTimeMillis() - startTime
        val combinedOutput = buildString {
            append(stdoutStream.toString(Charsets.UTF_8))
            append("\n")
            append(stderrStream.toString(Charsets.UTF_8))
            if (!exceptionMessage.isNullOrBlank() && !contains(exceptionMessage!!)) {
                append("\n")
                append(exceptionMessage)
            }
        }
        val errors = SourceErrorExtractor.extractErrors(combinedOutput)
        val failureReason = SourceErrorExtractor.extractFailureReason(combinedOutput)
            ?: exceptionMessage

        val runId = BuildLogStore.generateRunId()
        val cleanedLines = BuildLogStore.cleanAndDeduplicate(combinedOutput)
        BuildLogStore.saveLog(targetDir, runId, cleanedLines)

        val logExcerpt = if (!success) {
            SourceErrorExtractor.failureExcerpt(cleanedLines)
        } else {
            emptyList()
        }

        val failedTasks = taskOutcomes.filter { it.outcome == "FAILED" }.map { it.taskPath }
        val outputSummary = buildString {
            if (success) {
                append("Gradle run succeeded in ${executionTimeMs}ms.")
                append(" Requested: ${taskList.joinToString(", ")}.")
                append(" Executed ${taskOutcomes.size} task(s).")
            } else {
                append("Gradle run failed in ${executionTimeMs}ms.")
                append(" Requested: ${taskList.joinToString(", ")}.")
                if (failedTasks.isNotEmpty()) {
                    append(" Failed tasks: ${failedTasks.joinToString(", ")}.")
                }
                append(" Found ${errors.size} error location(s).")
                if (!failureReason.isNullOrBlank()) {
                    append(" Reason: ${failureReason.lines().first()}")
                }
            }
        }.trim()

        return GradleRunResult(
            runId = runId,
            success = success,
            executionTimeMs = executionTimeMs,
            requestedTasks = taskList,
            tasksExecuted = taskOutcomes,
            parsedErrors = errors,
            failureReason = if (success) null else failureReason,
            logExcerpt = logExcerpt,
            outputSummary = outputSummary
        )
    }

    override fun getRunLog(request: GradleRunLogRequest): GradleRunLogResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists()) { "Project directory does not exist: $targetDirPath" }

        return BuildLogStore.readLog(targetDir, request)
    }

    private fun mapOutcome(result: TaskOperationResult): String =
        when (result) {
            is TaskSuccessResult -> when {
                result.isUpToDate -> "UP-TO-DATE"
                result.isFromCache -> "FROM-CACHE"
                else -> "SUCCESS"
            }
            is TaskFailureResult -> "FAILED"
            is TaskSkippedResult -> "SKIPPED"
            else -> "UNKNOWN"
        }
}
