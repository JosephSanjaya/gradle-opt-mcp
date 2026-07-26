package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import com.gradle.optimization.mcp.features.configcache.api.ConfigurationCacheFeatureApi
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
class ConfigurationCacheToolsRegistrar(
    @Provided private val configCacheApi: ConfigurationCacheFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "audit_configuration_cache_inputs",
            description = "Audit configuration-time inputs and problems that affect Configuration Cache. " +
                "Parses the Gradle configuration-cache HTML report into input counts, notable inputs " +
                "(env/sys/file/custom sources), problems, and cache action. " +
                "Prefer this response over opening the HTML.",
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
                    put("maxNotableInputs", buildJsonObject { put("type", "integer") })
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
            val tasks = args["tasks"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val maxNotable = args["maxNotableInputs"]?.jsonPrimitive?.intOrNull
                ?: ConfigCacheAuditRequest.DEFAULT_MAX_NOTABLE_INPUTS

            val result = configCacheApi.auditConfigurationCacheInputs(
                ConfigCacheAuditRequest(
                    projectDir = projectDir,
                    tasks = tasks,
                    maxNotableInputs = maxNotable
                )
            )

            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Success: ${result.success}")
                appendLine("Cache Hit: ${result.cacheHit}")
                if (result.cacheAction != null) {
                    appendLine("Cache Action: ${result.cacheAction}")
                }
                if (result.requestedTasks.isNotEmpty()) {
                    appendLine("Requested Tasks: ${result.requestedTasks.joinToString(", ")}")
                }
                appendLine("Total Inputs: ${result.totalInputs}")
                appendLine("Total Problems: ${result.totalProblemCount}")
                if (!result.failureReason.isNullOrBlank()) {
                    appendLine("Failure Reason: ${result.failureReason}")
                }
                if (result.htmlReportPath != null) {
                    appendLine("HTML Report: ${result.htmlReportPath}")
                }

                if (result.inputCounts.isNotEmpty()) {
                    appendLine()
                    appendLine("Input Counts:")
                    result.inputCounts.forEach { count ->
                        appendLine("  - ${count.inputType}: ${count.count}")
                    }
                }

                if (result.problems.isNotEmpty()) {
                    appendLine()
                    appendLine("Problems (${result.problems.size}):")
                    result.problems.forEachIndexed { index, problem ->
                        appendLine("${index + 1}. ${problem.message}")
                        if (problem.location != null) appendLine("   Location: ${problem.location}")
                        if (problem.documentationLink != null) {
                            appendLine("   Docs: ${problem.documentationLink}")
                        }
                    }
                }

                if (result.notableInputs.isNotEmpty()) {
                    appendLine()
                    appendLine("Notable Inputs (${result.notableInputs.size}):")
                    result.notableInputs.forEach { input ->
                        appendLine("- [${input.inputType}] ${input.inputName}")
                        if (input.location != null) appendLine("  Location: ${input.location}")
                        if (input.antiPattern != null) appendLine("  Anti-pattern: ${input.antiPattern}")
                        if (input.recommendedRefactoring != null) {
                            appendLine("  Recommended: ${input.recommendedRefactoring}")
                        }
                        if (input.documentationLink != null) appendLine("  Docs: ${input.documentationLink}")
                    }
                }
            }.trimEnd()

            CallToolResult(
                content = listOf(TextContent(text = text)),
                isError = !result.success
            )
        }
    }
}
