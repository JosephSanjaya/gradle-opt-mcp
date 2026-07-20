package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaFeatureApi
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DeclarativeSchemaToolsRegistrar(
    @Provided private val declarativeApi: DeclarativeSchemaFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "inspect_declarative_schemas",
            description = "Inspect Declarative Gradle schemas, software types, and .dcl build configuration options.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("subprojectPath", buildJsonObject { put("type", "string") })
                },
                required = listOf("projectDir")
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.let { (it as? JsonPrimitive)?.content }
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: projectDir parameter is required")),
                    isError = true
                )
            val subprojectPath = args["subprojectPath"]?.let { (it as? JsonPrimitive)?.content }

            val result = declarativeApi.inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(
                    projectDir = projectDir,
                    subprojectPath = subprojectPath
                )
            )

            val softwareTypesStr = if (result.registeredSoftwareTypes.isEmpty()) {
                " None"
            } else {
                "\n" + result.registeredSoftwareTypes.joinToString("\n") {
                    " - ${it.name} (${it.targetType}) provided by ${it.providerPlugin}"
                }
            }

            val filesStr = if (result.declarativeFiles.isEmpty()) {
                " None"
            } else {
                "\n" + result.declarativeFiles.joinToString("\n") {
                    " - ${it.filePath} (${it.subproject}): elements [${it.elements.joinToString()}]"
                }
            }

            val warningsStr = if (result.warnings.isEmpty()) {
                " None"
            } else {
                "\n" + result.warnings.joinToString("\n") { " - WARNING: $it" }
            }

            val recsStr = "\n" + result.recommendations.joinToString("\n") { " - $it" }

            val summary = "Declarative Gradle Schema Inspection for $projectDir:\n\n" +
                "### Overview\n" +
                "- Declarative Gradle Detected: ${result.hasDeclarativeGradle}\n" +
                "- Declarative Files Count: ${result.declarativeFiles.size}\n\n" +
                "### Software Types:$softwareTypesStr\n\n" +
                "### Declarative Files (.dcl):$filesStr\n\n" +
                "### Warnings:$warningsStr\n\n" +
                "### Recommendations:$recsStr"

            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
