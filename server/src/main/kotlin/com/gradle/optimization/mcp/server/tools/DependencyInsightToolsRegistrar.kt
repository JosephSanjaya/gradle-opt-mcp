package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightFeatureApi
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightRequest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
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
            description = "Inspect Gradle dependency resolution for one module: selected version, " +
                "selection reasons, and capped dependency paths. " +
                "Defaults to the root project when modulePath is omitted. " +
                "Fails closed on unknown configuration; returns isError when the dependency is not found.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("modulePath", buildJsonObject { put("type", "string") })
                    put("configuration", buildJsonObject { put("type", "string") })
                    put("dependency", buildJsonObject { put("type", "string") })
                    put("maxPaths", buildJsonObject { put("type", "integer") })
                    put("maxReasons", buildJsonObject { put("type", "integer") })
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
            val modulePath = args["modulePath"]?.jsonPrimitive?.content
                ?: DependencyInsightRequest.DEFAULT_MODULE_PATH
            val maxPaths = args["maxPaths"]?.jsonPrimitive?.intOrNull
                ?: DependencyInsightRequest.DEFAULT_MAX_PATHS
            val maxReasons = args["maxReasons"]?.jsonPrimitive?.intOrNull
                ?: DependencyInsightRequest.DEFAULT_MAX_REASONS

            val result = runCatching {
                dependencyInsightApi.getDependencyInsight(
                    DependencyInsightRequest(
                        projectDir = projectDir,
                        configuration = configuration,
                        dependency = dependency,
                        modulePath = modulePath,
                        maxPaths = maxPaths,
                        maxReasons = maxReasons
                    )
                )
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Error: ${error.message ?: error}"
                        )
                    ),
                    isError = true
                )
            }

            val text = buildString {
                appendLine("Dependency Insight")
                appendLine("Project Dir: ${result.projectDir}")
                appendLine("Module Path: ${result.modulePath}")
                appendLine("Configuration: ${result.configuration}")
                appendLine("Dependency: ${result.dependency}")
                appendLine("Found: ${result.found}")
                if (result.selectedVersion != null) {
                    appendLine("Selected Version: ${result.selectedVersion}")
                }
                if (!result.failureReason.isNullOrBlank()) {
                    appendLine("Failure Reason: ${result.failureReason}")
                }
                if (result.reasons.isNotEmpty()) {
                    appendLine()
                    appendLine("Selection Reasons (${result.reasons.size}):")
                    result.reasons.forEach { reason ->
                        appendLine("- $reason")
                    }
                }
                if (result.paths.isNotEmpty()) {
                    appendLine()
                    appendLine("Paths (${result.paths.size}):")
                    result.paths.forEachIndexed { index, path ->
                        appendLine("${index + 1}.")
                        appendLine(path)
                    }
                }
            }.trimEnd()

            CallToolResult(
                content = listOf(TextContent(text = text)),
                isError = !result.found || !result.failureReason.isNullOrBlank()
            )
        }
    }
}
