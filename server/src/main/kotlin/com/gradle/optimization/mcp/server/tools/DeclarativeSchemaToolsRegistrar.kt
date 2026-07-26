package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaFeatureApi
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
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
class DeclarativeSchemaToolsRegistrar(
    @Provided private val declarativeApi: DeclarativeSchemaFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "inspect_declarative_schemas",
            description = "Static scan of Declarative Gradle .dcl files and known ecosystem plugin IDs " +
                "in settings/build scripts. Reports software-type blocks found in .dcl content, " +
                "scanned roots, and empty-state status (NOT_DECLARATIVE / NO_DCL / HAS_DCL). " +
                "Not a live schema registry or Tooling API software-type dump.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put(
                        "subprojectPath",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Optional Gradle project path (e.g. :features:foo) to scope the .dcl scan"
                            )
                        }
                    )
                    put("maxFiles", buildJsonObject { put("type", "integer") })
                    put("maxElementsPerFile", buildJsonObject { put("type", "integer") })
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
            val subprojectPath = args["subprojectPath"]?.jsonPrimitive?.content
            val maxFiles = args["maxFiles"]?.jsonPrimitive?.intOrNull
                ?: DeclarativeSchemaRequest.DEFAULT_MAX_FILES
            val maxElementsPerFile = args["maxElementsPerFile"]?.jsonPrimitive?.intOrNull
                ?: DeclarativeSchemaRequest.DEFAULT_MAX_ELEMENTS_PER_FILE

            val result = runCatching {
                declarativeApi.inspectDeclarativeSchemas(
                    DeclarativeSchemaRequest(
                        projectDir = projectDir,
                        subprojectPath = subprojectPath,
                        maxFiles = maxFiles,
                        maxElementsPerFile = maxElementsPerFile
                    )
                )
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Error: ${error.message ?: error::class.simpleName}"
                        )
                    ),
                    isError = true
                )
            }

            val filesStr = if (result.declarativeFiles.isEmpty()) {
                " None"
            } else {
                "\n" + result.declarativeFiles.joinToString("\n") { file ->
                    val elems = file.elements.joinToString()
                    val trunc = if (file.elementsTruncated) " [elements truncated]" else ""
                    " - ${file.filePath} (${file.subproject}): elements [$elems]$trunc"
                }
            }

            val softwareTypesStr = if (result.registeredSoftwareTypes.isEmpty()) {
                " None"
            } else {
                "\n" + result.registeredSoftwareTypes.joinToString("\n") {
                    " - ${it.name} (${it.targetType}) provided by ${it.providerPlugin}"
                }
            }

            val warningsStr = if (result.warnings.isEmpty()) {
                " None"
            } else {
                "\n" + result.warnings.joinToString("\n") { " - WARNING: $it" }
            }

            val recsStr = if (result.recommendations.isEmpty()) {
                " None"
            } else {
                "\n" + result.recommendations.joinToString("\n") { " - $it" }
            }

            val summary = result.summary + "\n\n" +
                "### Software Types:$softwareTypesStr\n\n" +
                "### Declarative Files (.dcl):$filesStr\n\n" +
                "### Warnings:$warningsStr\n\n" +
                "### Recommendations:$recsStr" +
                if (result.truncated) {
                    "\n\n(Truncated: showing ${result.declarativeFiles.size} of " +
                        "${result.declarativeFilesTotal} .dcl files; raise maxFiles to see more.)"
                } else {
                    ""
                }

            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
