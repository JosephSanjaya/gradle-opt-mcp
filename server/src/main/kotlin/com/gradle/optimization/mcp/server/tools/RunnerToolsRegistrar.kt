package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.runner.api.GradleRunLogRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunRequest
import com.gradle.optimization.mcp.features.runner.api.RunnerFeatureApi
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
            description = "Execute Gradle build tasks via the Gradle Tooling API, " +
                "capturing task status, duration, and source-mapped compiler/build errors.",
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
                }
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
            val tasks = args["tasks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val arguments = args["arguments"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            val result = runnerApi.runBuild(GradleRunRequest(projectDir, tasks, arguments))

            val text = buildString {
                appendLine("Summary: ${result.outputSummary}")
                if (result.runId != null) {
                    appendLine("Run ID: ${result.runId}")
                }
                appendLine("Success: ${result.success}")
                appendLine("Execution Time: ${result.executionTimeMs} ms")
                appendLine()
                if (result.tasksExecuted.isNotEmpty()) {
                    appendLine("Executed Tasks (${result.tasksExecuted.size}):")
                    result.tasksExecuted.forEach { task ->
                        appendLine("  ${task.taskPath} [${task.outcome}]")
                    }
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
                }
                if (result.runId != null) {
                    appendLine()
                    appendLine("Use 'gradle_run_log' with runId='${result.runId}' to inspect full build output logs.")
                }
            }.trimEnd()

            CallToolResult(content = listOf(TextContent(text = text)), isError = !result.success)
        }
    }

    @Suppress("MagicNumber")
    private fun registerRunLogTool(server: Server) {
        server.addTool(
            name = "gradle_run_log",
            description = "Retrieve detailed, cleaned, and deduplicated build logs for a previous Gradle execution " +
                "by runId.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("runId", buildJsonObject { put("type", "string") })
                    put("offset", buildJsonObject { put("type", "integer") })
                    put("limit", buildJsonObject { put("type", "integer") })
                    put("filter", buildJsonObject { put("type", "string") })
                },
                required = listOf("runId")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val runId = args["runId"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: runId parameter is required")),
                    isError = true
                )
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
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
}
