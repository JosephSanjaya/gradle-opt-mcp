package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import com.gradle.optimization.mcp.features.configcache.api.ConfigurationCacheFeatureApi
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
class ConfigurationCacheToolsRegistrar(
    @Provided private val configCacheApi: ConfigurationCacheFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "audit_configuration_cache_inputs",
            description = "Audit configuration-time inputs that trigger " +
                "Configuration Cache invalidations in a Gradle build.",
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

            val result = configCacheApi.auditConfigurationCacheInputs(ConfigCacheAuditRequest(projectDir))
            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Cache Hit: ${result.cacheHit}")
                if (result.htmlReportPath != null) {
                    appendLine("HTML Report: ${result.htmlReportPath}")
                }
                if (result.inputsAudited.isNotEmpty()) {
                    appendLine("\nInput Invalidations & Anti-Patterns Detected (${result.inputsAudited.size}):")
                    result.inputsAudited.forEach { violation ->
                        appendLine("- [${violation.inputType}] ${violation.inputName}")
                        appendLine("  Anti-pattern: ${violation.antiPattern}")
                        appendLine("  Recommended: ${violation.recommendedRefactoring}")
                    }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
