package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightFeatureApi
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightRequest
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
class DependencyInsightToolsRegistrar(
    @Provided private val dependencyInsightApi: DependencyInsightFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "get_dependency_insight",
            description = "Inspect Gradle dynamic dependency resolution, selection paths, and constraints.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("configuration", buildJsonObject { put("type", "string") })
                    put("dependency", buildJsonObject { put("type", "string") })
                },
                required = listOf("projectDir", "configuration", "dependency")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: projectDir parameter is required")),
                    isError = true
                )
            val configuration = args["configuration"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: configuration parameter is required")),
                    isError = true
                )
            val dependency = args["dependency"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: dependency parameter is required")),
                    isError = true
                )

            val result = dependencyInsightApi.getDependencyInsight(
                DependencyInsightRequest(
                    projectDir = projectDir,
                    configuration = configuration,
                    dependency = dependency
                )
            )
            val summary = "Dependency insight for '${result.dependency}' " +
                "in configuration '${result.configuration}':\n\n${result.insightOutput}"
            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
