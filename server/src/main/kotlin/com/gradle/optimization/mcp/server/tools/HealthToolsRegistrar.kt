package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.health.api.GradleHealthRequest
import com.gradle.optimization.mcp.features.health.api.HealthFeatureApi
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
class HealthToolsRegistrar(
    @Provided private val healthApi: HealthFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "gradle_health",
            description = "Perform a system & workspace orientation health check on a Gradle project, " +
                "returning Gradle/Java environment versions, project structure, wrapper properties, " +
                "configuration flags, and a gaps block for missing/mismatched setup.",
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

            val result = healthApi.checkHealth(GradleHealthRequest(projectDir = projectDir))

            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Project Dir: ${result.projectDir}")
                appendLine("Root Project: ${result.rootProjectName}")
                appendLine("Gradle Version: ${result.gradleVersion}")
                if (result.wrapperVersion != null) {
                    appendLine("Wrapper Version: ${result.wrapperVersion}")
                }
                appendLine("Java Version: ${result.javaVersion} (${result.javaVendor})")
                appendLine("Java Home: ${result.javaHome}")
                appendLine("OS: ${result.osName} (${result.osArch})")
                appendLine("Subprojects Count: ${result.subprojectCount}")
                if (result.subprojectNames.isNotEmpty()) {
                    appendLine("Subprojects: ${result.subprojectNames.joinToString(", ")}")
                }
                if (result.subprojectsTruncated) {
                    appendLine("Subprojects Truncated: true (showing first ${result.subprojectNames.size})")
                }
                appendLine("buildSrc Present: ${result.buildSrcPresent}")
                appendLine("Configuration Cache Enabled: ${result.configurationCacheEnabled}")
                appendLine("Build Cache Enabled: ${result.cachingEnabled}")
                appendLine("Parallel Enabled: ${result.parallelEnabled}")
                if (result.gaps.isEmpty()) {
                    appendLine("Gaps: none")
                } else {
                    appendLine("Gaps (${result.gaps.size}):")
                    result.gaps.forEach { appendLine("- $it") }
                }
            }

            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
