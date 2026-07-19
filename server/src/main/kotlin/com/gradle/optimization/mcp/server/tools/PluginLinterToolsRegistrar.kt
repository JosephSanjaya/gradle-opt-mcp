package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.linter.api.PluginLinterFeatureApi
import com.gradle.optimization.mcp.features.linter.api.PluginLinterRequest
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
class PluginLinterToolsRegistrar(
    @Provided private val linterApi: PluginLinterFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "lint_gradle_plugins",
            description = "Scans build scripts (*.gradle, *.gradle.kts) and custom plugin code " +
                "for performance anti-patterns and configuration cache violations.",
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

            val result = linterApi.lintPlugins(PluginLinterRequest(projectDir))
            val text = buildString {
                appendLine(result.summary)
                if (result.violations.isNotEmpty()) {
                    appendLine("\nViolations List:")
                    result.violations.forEach { violation ->
                        appendLine("- [${violation.ruleId}] ${violation.file}:${violation.line}")
                        appendLine("  Message: ${violation.message}")
                        appendLine("  Recommendation: ${violation.recommendation}")
                        appendLine("  Snippet: ${violation.snippet}")
                    }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
