package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckRequest
import com.gradle.optimization.mcp.features.isolation.api.ProjectIsolationFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class ProjectIsolationToolsRegistrar(
    @Provided private val isolationApi: ProjectIsolationFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "check_project_isolation_violations",
            description = "Check target Gradle project for Project Isolation violations.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
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
            val result = isolationApi.checkProjectIsolation(IsolationCheckRequest(projectDir))
            val summary = if (result.isIsolated) {
                "No project isolation violations detected in $projectDir."
            } else {
                "Project isolation violations detected (${result.violations.size}):\n" +
                    result.violations.joinToString("\n") { "- ${it.message}" }
            }
            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
