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
import kotlinx.serialization.json.intOrNull
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
            description = "Summary-first resolved dependency inventory: counts, GAV-grouped conflicts, and a " +
                "capped direct-dependency list. Default configs are compileClasspath/runtimeClasspath " +
                "(pass configuration=all for every resolvable config). Requires projectDir.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("modulePath", buildJsonObject { put("type", "string") })
                    put("configuration", buildJsonObject { put("type", "string") })
                    put("includeTransitive", buildJsonObject { put("type", "boolean") })
                    put("onlyConflicts", buildJsonObject { put("type", "boolean") })
                    put("maxDependencies", buildJsonObject { put("type", "integer") })
                    put("maxConflicts", buildJsonObject { put("type", "integer") })
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
            val modulePath = args["modulePath"]?.jsonPrimitive?.content
            val configuration = args["configuration"]?.jsonPrimitive?.content
            val includeTransitive = args["includeTransitive"]?.jsonPrimitive?.booleanOrNull ?: false
            val onlyConflicts = args["onlyConflicts"]?.jsonPrimitive?.booleanOrNull ?: false
            val maxDependencies = args["maxDependencies"]?.jsonPrimitive?.intOrNull
                ?: GradleDepsRequest.DEFAULT_MAX_DEPENDENCIES
            val maxConflicts = args["maxConflicts"]?.jsonPrimitive?.intOrNull
                ?: GradleDepsRequest.DEFAULT_MAX_CONFLICTS

            val result = runCatching {
                dependencyGraphApi.getDependencyGraph(
                    GradleDepsRequest(
                        projectDir = projectDir,
                        modulePath = modulePath,
                        configuration = configuration,
                        includeTransitive = includeTransitive,
                        onlyConflicts = onlyConflicts,
                        maxDependencies = maxDependencies,
                        maxConflicts = maxConflicts
                    )
                )
            }.getOrElse { error ->
                return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Error: ${error.message}")),
                    isError = true
                )
            }

            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Project Dir: ${result.projectDir}")
                appendLine("Total Dependencies: ${result.totalDependencies}")
                appendLine("Direct: ${result.directCount} | Transitive: ${result.transitiveCount}")
                appendLine(
                    "Conflicts: ${result.conflictCount} nodes / ${result.conflictGroupCount} GAV groups"
                )
                appendLine("Truncated: ${result.truncated}")
                val configs = result.configurationsScanned.joinToString(", ").ifEmpty { "(none)" }
                appendLine("Configurations Scanned: $configs")
                val modulesStr = result.modulesAnalyzed.joinToString(", ")
                appendLine("Modules Analyzed (${result.modulesAnalyzed.size}): $modulesStr")

                if (result.errors.isNotEmpty()) {
                    appendLine()
                    appendLine("Resolution Warnings / Errors (${result.errors.size}):")
                    result.errors.take(MAX_ERRORS_IN_RESPONSE).forEach { err ->
                        appendLine("  [WARN] $err")
                    }
                }

                if (result.conflicts.isNotEmpty()) {
                    appendLine()
                    appendLine("Top Conflicts (by GAV, capped ${result.conflicts.size}):")
                    result.conflicts.forEachIndexed { index, conflict ->
                        val requested = conflict.requestedVersions.joinToString(", ")
                        appendLine(
                            "${index + 1}. ${conflict.group}:${conflict.name} " +
                                "requested=[$requested] -> resolved=${conflict.resolvedVersion}"
                        )
                        appendLine("   modules=${conflict.modules.joinToString(", ")}")
                        appendLine("   reason=${conflict.selectionReason.formatCompact()}")
                    }
                }

                if (result.dependencies.isNotEmpty()) {
                    appendLine()
                    appendLine("Dependencies (capped ${result.dependencies.size}):")
                    result.dependencies.forEachIndexed { index, node ->
                        val scope = if (node.isDirect) "DIRECT" else "TRANSITIVE"
                        val conflictFlag = if (node.hasConflict) " [CONFLICT]" else ""
                        val verStr = when {
                            node.componentKind == "project" -> "(project)"
                            node.hasConflict ->
                                "requested: ${node.requestedVersion} -> resolved: ${node.resolvedVersion}"
                            else -> node.resolvedVersion
                        }
                        val prefix =
                            "${index + 1}. [${node.modulePath}] [${node.configuration}] [$scope]$conflictFlag"
                        val artifact = "${node.group}:${node.name}:$verStr"
                        appendLine("$prefix $artifact (${node.selectionReason.formatCompact()})")
                    }
                } else {
                    appendLine()
                    appendLine("No dependencies matched the specified criteria.")
                }
            }.trimEnd()

            CallToolResult(content = listOf(TextContent(text = text)), isError = false)
        }
    }

    companion object {
        private const val MAX_ERRORS_IN_RESPONSE = 10
    }
}
