package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.runner.api.GradleRunLogRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunResult
import com.gradle.optimization.mcp.features.runner.api.RunnerFeatureApi
import com.gradle.optimization.mcp.features.runner.api.TaskOutcome
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class RunnerToolsRegistrar(
    @Provided private val runnerApi: RunnerFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        registerRunTool(server)
        registerRunLogTool(server)
    }

    private fun registerRunTool(server: Server) {
        server.addTool(
            name = "gradle_run",
            description = "Execute Gradle build tasks via the Gradle Tooling API. " +
                "Returns task outcomes, source-mapped errors, failure reason, and a log excerpt on failure. " +
                "Prefer this over gradle_run_log; only fetch logs when the excerpt is insufficient.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put(
                        "tasks",
                        buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                        }
                    )
                    put(
                        "arguments",
                        buildJsonObject {
                            put("type", "array")
                            put("items", buildJsonObject { put("type", "string") })
                        }
                    )
                },
                required = listOf("projectDir")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: projectDir parameter is required")),
                    isError = true
                )
            val tasks = args["tasks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val arguments = args["arguments"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            val result = runnerApi.runBuild(GradleRunRequest(projectDir, tasks, arguments))
            CallToolResult(content = listOf(TextContent(text = formatRunResult(result))), isError = !result.success)
        }
    }

    @Suppress("MagicNumber")
    private fun registerRunLogTool(server: Server) {
        server.addTool(
            name = "gradle_run_log",
            description = "Escape hatch for full cleaned build logs by runId. " +
                "Prefer gradle_run's failure excerpt first. " +
                "Use filter='failure' to jump to the failure slice; other filter values are substring matches.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("runId", buildJsonObject { put("type", "string") })
                    put("offset", buildJsonObject { put("type", "integer") })
                    put("limit", buildJsonObject { put("type", "integer") })
                    put("filter", buildJsonObject { put("type", "string") })
                },
                required = listOf("projectDir", "runId")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: projectDir parameter is required")),
                    isError = true
                )
            val runId = args["runId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: runId parameter is required")),
                    isError = true
                )
            val offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0
            val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 200
            val filter = args["filter"]?.jsonPrimitive?.content

            val logResult = runnerApi.getRunLog(
                GradleRunLogRequest(
                    projectDir = projectDir,
                    runId = runId,
                    offset = offset,
                    limit = limit,
                    filter = filter
                )
            )

            val text = buildString {
                appendLine("Run ID: ${logResult.runId}")
                val countInfo = "showing ${logResult.lines.size}, offset ${logResult.offset}"
                appendLine("Total Lines: ${logResult.totalLines} ($countInfo)")
                appendLine("Has More: ${logResult.hasMore}")
                appendLine()
                logResult.lines.forEach { appendLine(it) }
            }.trimEnd()

            CallToolResult(content = listOf(TextContent(text = text)))
        }
    }

    private fun formatRunResult(result: GradleRunResult): String = buildString {
        appendLine("Summary: ${result.outputSummary}")
        if (result.runId != null) {
            appendLine("Run ID: ${result.runId}")
        }
        appendLine("Success: ${result.success}")
        appendLine("Execution Time: ${result.executionTimeMs} ms")
        if (result.requestedTasks.isNotEmpty()) {
            appendLine("Requested Tasks: ${result.requestedTasks.joinToString(", ")}")
        }
        appendLine()

        val notable = notableTasks(result.tasksExecuted)
        if (notable.isNotEmpty()) {
            appendLine("Notable Tasks (${notable.size}/${result.tasksExecuted.size}):")
            notable.forEach { task ->
                appendLine("  ${task.taskPath} [${task.outcome}]")
            }
            appendLine()
        } else if (result.tasksExecuted.isNotEmpty()) {
            appendLine("Tasks: ${result.tasksExecuted.size} completed (all UP-TO-DATE/SKIPPED).")
            appendLine()
        }

        if (!result.failureReason.isNullOrBlank()) {
            appendLine("Failure Reason:")
            result.failureReason!!.lines().forEach { appendLine("  $it") }
            appendLine()
        }

        if (result.parsedErrors.isNotEmpty()) {
            appendLine("Parsed Errors (${result.parsedErrors.size}):")
            result.parsedErrors.forEachIndexed { index, error ->
                val locationStr = buildString {
                    if (error.file != null) append(error.file)
                    if (error.line != null) append(":${error.line}")
                    if (error.column != null) append(":${error.column}")
                }
                val loc = if (locationStr.isNotEmpty()) locationStr else "General Error"
                appendLine("${index + 1}. [${error.errorType}] $loc")
                appendLine("   ${error.message}")
                if (error.task != null) appendLine("   Task: ${error.task}")
                if (error.snippet != null) appendLine("   Snippet: ${error.snippet}")
            }
            appendLine()
        }

        if (result.logExcerpt.isNotEmpty()) {
            appendLine("Log Excerpt (${result.logExcerpt.size} lines):")
            result.logExcerpt.forEach { appendLine(it) }
            appendLine()
        }

        if (!result.success && result.runId != null &&
            result.parsedErrors.isEmpty() && result.logExcerpt.isEmpty()
        ) {
            appendLine(
                "Insufficient failure detail in primary response. " +
                    "Use gradle_run_log with runId='${result.runId}' " +
                    "(filter='failure' recommended) for full logs."
            )
        }
    }.trimEnd()

    private fun notableTasks(tasks: List<TaskOutcome>): List<TaskOutcome> {
        val noise = setOf("UP-TO-DATE", "SKIPPED")
        val interesting = tasks.filter { it.outcome !in noise }
        if (interesting.isNotEmpty()) return interesting
        return emptyList()
    }
}
