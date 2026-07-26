package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckRequest
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckResult
import com.gradle.optimization.mcp.features.isolation.api.ProjectIsolationFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
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
            description = "Check a Gradle project for Project Isolation violations " +
                "(-Dorg.gradle.unsafe.isolated-projects=true). Parses the configuration-cache / " +
                "isolated-projects HTML report into capped structured violations with locations. " +
                "Fails closed on missing/invalid projectDir or Tooling API failures without isolation signal.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("recreateCache", buildJsonObject { put("type", "boolean") })
                    put("maxViolations", buildJsonObject { put("type", "integer") })
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
            val recreateCache = args["recreateCache"]?.jsonPrimitive?.booleanOrNull ?: false
            val maxViolations = args["maxViolations"]?.jsonPrimitive?.intOrNull
                ?: IsolationCheckRequest.DEFAULT_MAX_VIOLATIONS

            val result = runCatching {
                isolationApi.checkProjectIsolation(
                    IsolationCheckRequest(
                        projectDir = projectDir,
                        recreateCache = recreateCache,
                        maxViolations = maxViolations
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

    private fun formatResult(result: IsolationCheckResult): String = buildString {
        appendLine("Summary: ${result.summary}")
        appendLine("Project Dir: ${result.projectDir}")
        appendLine("Success: ${result.success}")
        appendLine("Isolated: ${result.isIsolated}")
        appendLine("Total Violations: ${result.totalViolationCount}")
        if (!result.failureReason.isNullOrBlank()) {
            appendLine("Failure Reason: ${result.failureReason}")
        }
        if (result.htmlReportPath != null) {
            appendLine("HTML Report: ${result.htmlReportPath}")
        }
        if (result.violations.isNotEmpty()) {
            appendLine()
            appendLine("Violations (${result.violations.size}):")
            result.violations.forEachIndexed { index, violation ->
                appendLine("${index + 1}. [${violation.violationType}] ${violation.message}")
                if (violation.location != null) appendLine("   Location: ${violation.location}")
                if (violation.sourceFile != null) {
                    val line = violation.lineNumber?.let { ":$it" }.orEmpty()
                    appendLine("   Source: ${violation.sourceFile}$line")
                }
                if (violation.documentationLink != null) {
                    appendLine("   Docs: ${violation.documentationLink}")
                }
            }
        }
    }.trimEnd()
}
