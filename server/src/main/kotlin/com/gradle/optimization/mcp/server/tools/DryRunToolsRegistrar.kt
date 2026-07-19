package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.dryrun.api.DryRunFeatureApi
import com.gradle.optimization.mcp.features.dryrun.api.DryRunRequest
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

@Single
class DryRunToolsRegistrar(
    @Provided private val dryRunApi: DryRunFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "analyze_build_dry_run",
            description = "Execute a Gradle dry-run build (--dry-run) and analyze planned task execution sequence.",
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
            val result = dryRunApi.analyzeDryRun(DryRunRequest(projectDir, tasks))
            val taskList = result.tasksExecuted.joinToString("\n") {
                "${it.taskPath}${if (it.skipped) " (SKIPPED)" else ""}"
            }
            val summary = "Dry run completed for project $projectDir.\n" +
                "Tasks planned: ${result.tasksExecuted.size}\n\n$taskList"
            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
