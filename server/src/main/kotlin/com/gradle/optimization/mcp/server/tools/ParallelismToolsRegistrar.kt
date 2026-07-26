package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisRequest
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisResult
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismCount
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private const val GROUP_DISPLAY_LIMIT = 15

@Single
class ParallelismToolsRegistrar(
    @Provided private val parallelismApi: ParallelismFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "analyze_parallelization_bottlenecks",
            description = "Summarize a Gradle --dry-run task graph for parallelism heuristics: " +
                "taskCount, byModule/byLeafType histograms, capped heuristicCriticalTasks " +
                "(not a real critical path), and bottleneck module hints. " +
                "Recommended tasks: classes, assemble, or build — avoid help. " +
                "Fails closed on non-Gradle dirs / Tooling errors; EMPTY_GRAPH / SHALLOW_GRAPH " +
                "when the schedule has no useful signal.",
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
            val tasks = args["tasks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

            val result = runCatching {
                parallelismApi.analyzeParallelizationBottlenecks(
                    ParallelismAnalysisRequest(
                        projectDir = projectDir,
                        tasks = tasks
                    )
                )
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: ${error.message ?: error}")),
                    isError = true
                )
            }

            CallToolResult(
                content = listOf(TextContent(text = formatResult(result))),
                isError = !result.success
            )
        }
    }

    private fun formatResult(result: ParallelismAnalysisResult): String = buildString {
        appendLine("Summary: ${result.summary}")
        appendLine("Project Dir: ${result.projectDir}")
        appendLine("Success: ${result.success}")
        appendLine("Analysis Status: ${result.analysisStatus}")
        appendLine("Requested Tasks: ${result.requestedTasks.joinToString(", ")}")
        appendLine("Task Count: ${result.taskCount}")
        appendLine("Caveat: ${result.caveat}")

        if (!result.guidance.isNullOrBlank()) {
            appendLine("Guidance: ${result.guidance}")
        }

        appendCountSection("By Module", result.byModule)
        appendCountSection("By Leaf Type", result.byLeafType)

        if (result.bottleneckModules.isNotEmpty()) {
            appendLine()
            appendLine("Bottleneck Modules (heuristic):")
            result.bottleneckModules.forEach { appendLine("- $it") }
        }

        if (result.heuristicCriticalTasks.isNotEmpty()) {
            appendLine()
            val shown = result.heuristicCriticalTasks.size
            appendLine("Heuristic Critical Tasks Sample ($shown):")
            result.heuristicCriticalTasks.forEach { appendLine("- ${it.taskPath}") }
            if (result.heuristicCriticalTasksTruncated) {
                appendLine("Truncated: true")
            }
        }

        if (!result.failureReason.isNullOrBlank()) {
            appendLine()
            appendLine("Failure Reason:")
            result.failureReason!!.lines().forEach { appendLine("  $it") }
        }
    }.trimEnd()

    private fun StringBuilder.appendCountSection(title: String, counts: List<ParallelismCount>) {
        if (counts.isEmpty()) return
        appendLine()
        val shown = counts.take(GROUP_DISPLAY_LIMIT)
        val omitted = counts.size - shown.size
        appendLine("$title (${counts.size}):")
        shown.forEach { appendLine("  ${it.name}: ${it.count}") }
        if (omitted > 0) {
            appendLine("  … and $omitted more")
        }
    }
}
