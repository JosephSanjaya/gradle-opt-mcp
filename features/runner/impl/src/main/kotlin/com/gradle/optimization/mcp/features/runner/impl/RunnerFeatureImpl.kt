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
import org.gradle.tooling.events.task.TaskFinishEvent
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

        val success = runCatching {
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                    .forTasks(*taskList.toTypedArray())
                    .setStandardOutput(stdoutStream)
                    .setStandardError(stderrStream)
                    .addProgressListener(
                        ProgressListener { event: ProgressEvent ->
                            if (event is TaskFinishEvent) {
                                val outcomeStr = when (val result = event.result) {
                                    is TaskSuccessResult -> when {
                                        result.isUpToDate -> "UP-TO-DATE"
                                        result.isFromCache -> "FROM-CACHE"
                                        else -> "SUCCESS"
                                    }
                                    else -> result.javaClass.simpleName.replace("ResultImpl", "").uppercase()
                                }
                                taskOutcomes.add(TaskOutcome(event.descriptor.taskPath, outcomeStr))
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
        }.getOrElse { false }

        val executionTimeMs = System.currentTimeMillis() - startTime
        val combinedOutput = buildString {
            append(stdoutStream.toString(Charsets.UTF_8))
            append("\n")
            append(stderrStream.toString(Charsets.UTF_8))
        }
        val errors = SourceErrorExtractor.extractErrors(combinedOutput)

        val runId = BuildLogStore.generateRunId()
        val cleanedLines = BuildLogStore.cleanAndDeduplicate(combinedOutput)
        BuildLogStore.saveLog(targetDir, runId, cleanedLines)

        val outputSummary = buildString {
            if (success) {
                append("Gradle run succeeded in ${executionTimeMs}ms. Executed ${taskOutcomes.size} task(s).")
            } else {
                append("Gradle run failed in ${executionTimeMs}ms. Found ${errors.size} error location(s).")
            }
        }.trim()

        return GradleRunResult(
            runId = runId,
            success = success,
            executionTimeMs = executionTimeMs,
            tasksExecuted = taskOutcomes,
            parsedErrors = errors,
            outputSummary = outputSummary
        )
    }

    override fun getRunLog(request: GradleRunLogRequest): GradleRunLogResult {
        val targetDirPath = request.projectDir ?: config.defaultProjectDir
        val targetDir = File(targetDirPath)
        require(targetDir.exists()) { "Project directory does not exist: $targetDirPath" }

        return BuildLogStore.readLog(targetDir, request)
    }
}
