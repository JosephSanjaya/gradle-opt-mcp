package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileRequest
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfilerFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private const val DEFAULT_LIMIT = 10

@Single
class CacheProfilerToolsRegistrar(
    @Provided private val cacheProfilerApi: CacheProfilerFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "profile_cache_invalidation_timeline",
            description = "Profile historical Configuration Cache invalidations and " +
                "inspect input diffs across consecutive builds.",
            inputSchema = ToolSchema(
                properties = kotlinx.serialization.json.buildJsonObject {
                    put("projectDir", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
                    put("limit", kotlinx.serialization.json.buildJsonObject { put("type", "integer") })
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
            val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_LIMIT

            val result = cacheProfilerApi.profileCacheInvalidationTimeline(
                CacheProfileRequest(projectDir = projectDir, limit = limit)
            )

            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Total Entries Found: ${result.totalEntriesFound}")
                if (result.entries.isNotEmpty()) {
                    appendLine("\nCache Invalidation Timeline:")
                    result.entries.forEach { entry ->
                        appendLine("- [${entry.status}] ${entry.entryId} at ${entry.formattedTime}")
                        if (entry.inputDiffSummary != null) {
                            appendLine("  Diff: ${entry.inputDiffSummary}")
                        }
                        if (entry.invalidationReasons.isNotEmpty()) {
                            appendLine("  Reasons: ${entry.invalidationReasons.joinToString("; ")}")
                        }
                    }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
