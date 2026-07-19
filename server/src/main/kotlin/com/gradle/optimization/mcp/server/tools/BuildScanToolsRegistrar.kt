package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.buildscan.api.BuildScanFeatureApi
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
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

private const val PERCENTAGE_MULTIPLIER = 100.0

@Single
class BuildScanToolsRegistrar(
    @Provided private val buildScanApi: BuildScanFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "analyze_build_scan",
            description = "Analyze build scan execution telemetry, cache hit/miss ratio, and GC pauses.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("projectDir", buildJsonObject { put("type", "string") })
                    put("buildScanUrl", buildJsonObject { put("type", "string") })
                    put("dumpFilePath", buildJsonObject { put("type", "string") })
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
            val buildScanUrl = args["buildScanUrl"]?.let { (it as? JsonPrimitive)?.content }
            val dumpFilePath = args["dumpFilePath"]?.let { (it as? JsonPrimitive)?.content }

            val result = buildScanApi.analyzeBuildScan(
                BuildScanRequest(
                    projectDir = projectDir,
                    buildScanUrl = buildScanUrl,
                    dumpFilePath = dumpFilePath
                )
            )

            val cachePct = result.cacheHitRatio * PERCENTAGE_MULTIPLIER
            val longRunningStr = if (result.longRunningTasks.isEmpty()) {
                " None"
            } else {
                "\n" + result.longRunningTasks.joinToString("\n") {
                    " - ${it.taskPath} (${it.durationMs}ms, ${it.outcome})"
                }
            }

            val missesStr = if (result.tasksWithCacheMisses.isEmpty()) {
                " None"
            } else {
                "\n" + result.tasksWithCacheMisses.joinToString("\n") {
                    " - ${it.taskPath} (${it.outcome})"
                }
            }

            val recsStr = "\n" + result.recommendations.joinToString("\n") { " - $it" }

            val summary = "Build scan telemetry analysis for $projectDir:\n\n" +
                "### Summary Metrics\n" +
                "- Total Tasks Analyzed: ${result.totalTasksCount}\n" +
                "- Cache Hit Ratio: ${"%.2f".format(cachePct)}% " +
                "(${result.cacheHitCount} hits, ${result.cacheMissCount} misses)\n" +
                "- GC Pause Duration: ${result.gcPauseMs} ms\n\n" +
                "### Longest Running Tasks:$longRunningStr\n\n" +
                "### Tasks with Cache Misses:$missesStr\n\n" +
                "### Actionable Optimization Advice:$recsStr"

            CallToolResult(content = listOf(TextContent(text = summary)))
        }
    }
}
