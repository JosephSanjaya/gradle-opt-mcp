package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.dryrun.api.DryRunCount
import com.gradle.optimization.mcp.features.dryrun.api.DryRunFeatureApi
import com.gradle.optimization.mcp.features.dryrun.api.DryRunRequest
import com.gradle.optimization.mcp.features.dryrun.api.DryRunResult
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
class DryRunToolsRegistrar(
    @Provided private val dryRunApi: DryRunFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "analyze_build_dry_run",
            description = "Analyze the Gradle task execution graph with --dry-run (no compilers/tests). " +
                "Returns taskCount, module/leaf-type groupings, a capped scheduled-task sample, " +
                "and failureReason on Tooling API errors.",
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
            val tasks = args["tasks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            val result = runCatching {
                dryRunApi.analyzeDryRun(DryRunRequest(projectDir, tasks))
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(text = "Error: ${error.message ?: error}")
                    ),
                    isError = true
                )
            }

            CallToolResult(
                content = listOf(TextContent(text = formatResult(result))),
                isError = !result.success
            )
        }
    }

    private fun formatResult(result: DryRunResult): String = buildString {
        appendLine("Summary: ${result.summary}")
        appendLine("Success: ${result.success}")
        appendLine("Requested Tasks: ${result.requestedTasks.joinToString(", ")}")
        appendLine("Task Count: ${result.taskCount}")

        appendCountSection("By Module", result.byModule)
        appendCountSection("By Leaf Type", result.byLeafType)

        if (result.sampleTasks.isNotEmpty()) {
            appendLine()
            appendLine("Sample Tasks (${result.sampleTasks.size}/${result.taskCount}):")
            result.sampleTasks.forEach { task ->
                appendLine("  ${task.taskPath} [${task.status}]")
            }
        }

        if (!result.failureReason.isNullOrBlank()) {
            appendLine()
            appendLine("Failure Reason:")
            result.failureReason!!.lines().forEach { appendLine("  $it") }
        }
    }.trimEnd()

    private fun StringBuilder.appendCountSection(title: String, counts: List<DryRunCount>) {
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
