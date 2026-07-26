package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.linter.api.PluginLinterFeatureApi
import com.gradle.optimization.mcp.features.linter.api.PluginLinterRequest
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
class PluginLinterToolsRegistrar(
    @Provided private val linterApi: PluginLinterFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "lint_gradle_plugins",
            description = "Static anti-pattern scan of build scripts (*.gradle, *.gradle.kts) and " +
                "buildSrc/build-logic plugin sources for eager task creation, unsafe .all queries, " +
                "project. access inside task actions, and Provider/Property.toString(). " +
                "Not a full Configuration Cache audit — use audit_configuration_cache_inputs for " +
                "runtime CC inputs and problems.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("maxFindings", buildJsonObject { put("type", "integer") })
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
            val maxFindings = args["maxFindings"]?.jsonPrimitive?.intOrNull
                ?: PluginLinterRequest.DEFAULT_MAX_FINDINGS

            val result = linterApi.lintPlugins(
                PluginLinterRequest(projectDir = projectDir, maxFindings = maxFindings)
            )
            val text = buildString {
                appendLine(result.summary)
                if (result.violations.isNotEmpty()) {
                    val listLabel = if (result.truncated) {
                        "Violations List (${result.violations.size} of ${result.totalViolations}, truncated):"
                    } else {
                        "Violations List (${result.violations.size}):"
                    }
                    appendLine()
                    appendLine(listLabel)
                    result.violations.forEach { violation ->
                        appendLine("- [${violation.ruleId}] ${violation.file}:${violation.line}")
                        appendLine("  Message: ${violation.message}")
                        appendLine("  Recommendation: ${violation.recommendation}")
                        appendLine("  Snippet: ${violation.snippet}")
                    }
                    if (result.truncated) {
                        appendLine(
                            "\n(Truncated: showing ${result.violations.size} of " +
                                "${result.totalViolations}; raise maxFindings to see more.)"
                        )
                    }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
