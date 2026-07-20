package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.health.api.GradleHealthRequest
import com.gradle.optimization.mcp.features.health.api.HealthFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
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
                "and build configuration details.",
            inputSchema = ToolSchema(
                properties = kotlinx.serialization.json.buildJsonObject {
                    put("projectDir", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
                }
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content

            val result = healthApi.checkHealth(GradleHealthRequest(projectDir = projectDir))

            val text = buildString {
                appendLine("Summary: ${result.summary}")
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
                appendLine("buildSrc Present: ${result.buildSrcPresent}")
                appendLine("Configuration Cache Configured: ${result.configurationCacheConfigFile}")
            }

            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
