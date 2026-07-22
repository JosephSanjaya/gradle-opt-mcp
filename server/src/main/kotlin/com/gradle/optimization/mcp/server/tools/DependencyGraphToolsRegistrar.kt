package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyGraphFeatureApi
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DependencyGraphToolsRegistrar(
    @Provided private val dependencyGraphApi: DependencyGraphFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "gradle_deps",
            description = "Extract flat resolved dependencies, transitive nodes, version conflicts, and selection " +
                "reasons across Gradle modules and configurations.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("modulePath", buildJsonObject { put("type", "string") })
                    put("configuration", buildJsonObject { put("type", "string") })
                    put("includeTransitive", buildJsonObject { put("type", "boolean") })
                    put("onlyConflicts", buildJsonObject { put("type", "boolean") })
                }
            )
        ) { request ->
            val args = request.params.arguments ?: JsonObject(emptyMap())
            val projectDir = args["projectDir"]?.jsonPrimitive?.content
            val modulePath = args["modulePath"]?.jsonPrimitive?.content
            val configuration = args["configuration"]?.jsonPrimitive?.content
            val includeTransitive = args["includeTransitive"]?.jsonPrimitive?.booleanOrNull ?: true
            val onlyConflicts = args["onlyConflicts"]?.jsonPrimitive?.booleanOrNull ?: false

            val result = dependencyGraphApi.getDependencyGraph(
                GradleDepsRequest(
                    projectDir = projectDir,
                    modulePath = modulePath,
                    configuration = configuration,
                    includeTransitive = includeTransitive,
                    onlyConflicts = onlyConflicts
                )
            )

            val text = buildString {
                appendLine("Gradle Resolved Dependency Graph:")
                appendLine("Project Dir: ${result.projectDir}")
                appendLine("Total Dependencies: ${result.totalDependencies}")
                appendLine("Conflicts Detected: ${result.conflictCount}")
                val modulesStr = result.modulesAnalyzed.joinToString(", ")
                appendLine("Modules Analyzed (${result.modulesAnalyzed.size}): $modulesStr")
                appendLine()

                if (result.errors.isNotEmpty()) {
                    appendLine("Resolution Warnings / Errors (${result.errors.size}):")
                    result.errors.forEach { err ->
                        appendLine("  [WARN] $err")
                    }
                    appendLine()
                }

                if (result.dependencies.isNotEmpty()) {
                    appendLine("Dependencies List:")
                    result.dependencies.forEachIndexed { index, node ->
                        val scope = if (node.isDirect) "DIRECT" else "TRANSITIVE"
                        val conflictFlag = if (node.hasConflict) " [CONFLICT]" else ""
                        val verStr = if (node.hasConflict) {
                            "requested: ${node.requestedVersion} -> resolved: ${node.resolvedVersion}"
                        } else {
                            node.resolvedVersion
                        }
                        val prefix = "${index + 1}. [${node.modulePath}] [${node.configuration}] [$scope]$conflictFlag"
                        val artifact = "${node.group}:${node.name}:$verStr"
                        appendLine("$prefix $artifact (${node.selectionReason})")
                    }
                } else {
                    appendLine("No dependencies matched the specified criteria.")
                }
            }.trimEnd()

            CallToolResult(content = listOf(TextContent(text = text)), isError = false)
        }
    }
}
