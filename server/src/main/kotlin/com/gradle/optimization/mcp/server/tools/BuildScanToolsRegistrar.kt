package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.buildscan.api.BuildScanFeatureApi
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanResult
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
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
            description = "Parse a local plain-text Gradle task-outcome dump for cache hit/miss " +
                "ratio, longest timed tasks, and GC pauses. Requires projectDir. " +
                "Provide dumpFilePath to a supported dump (task lines like " +
                "`:module:task FROM-CACHE 120ms`; optional `GC pause: Nms`). " +
                "Fails closed with NO_DATA when no dump is provided. " +
                "Does not run Gradle and does not fetch remote build scans.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "projectDir",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Absolute path to the Gradle project root")
                        }
                    )
                    put(
                        "dumpFilePath",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "Absolute path to a local plain-text task-outcome dump " +
                                    "(required for analysis; missing/unreadable → error)"
                            )
                        }
                    )
                    put(
                        "maxListedTasks",
                        buildJsonObject {
                            put("type", "integer")
                            put(
                                "description",
                                "Cap for longest-running and cache-miss task lists " +
                                    "(default ${BuildScanRequest.DEFAULT_MAX_LISTED_TASKS})"
                            )
                        }
                    )
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
            // Not advertised in schema; if a client still sends it, fail closed (unsupported).
            val buildScanUrl = args["buildScanUrl"]?.let { (it as? JsonPrimitive)?.content }
            val dumpFilePath = args["dumpFilePath"]?.let { (it as? JsonPrimitive)?.content }
            val maxListedTasks = args["maxListedTasks"]?.let { (it as? JsonPrimitive)?.intOrNull }
                ?: BuildScanRequest.DEFAULT_MAX_LISTED_TASKS

            val result = buildScanApi.analyzeBuildScan(
                BuildScanRequest(
                    projectDir = projectDir,
                    buildScanUrl = buildScanUrl,
                    dumpFilePath = dumpFilePath,
                    maxListedTasks = maxListedTasks
                )
            )

            CallToolResult(
                content = listOf(TextContent(text = formatResult(result))),
                isError = result.status != BuildScanResult.STATUS_OK
            )
        }
    }

    private fun formatResult(result: BuildScanResult): String = buildString {
        appendLine("Build scan dump analysis for ${result.projectDir}")
        appendLine("Status: ${result.status}")
        val failureReason = result.failureReason
        if (failureReason != null) {
            appendLine("Failure Reason: $failureReason")
        }
        val guidance = result.guidance
        if (guidance != null) {
            appendLine("Guidance: $guidance")
        }
        val dumpFormatHint = result.dumpFormatHint
        if (dumpFormatHint != null && result.status != BuildScanResult.STATUS_OK) {
            appendLine("Dump Format: $dumpFormatHint")
        }

        if (result.status != BuildScanResult.STATUS_OK) {
            return@buildString
        }

        appendLine()
        appendLine("### Summary Metrics")
        appendLine("- Total Tasks Analyzed: ${result.totalTasksCount}")
        val ratio = result.cacheHitRatio
        if (ratio != null) {
            val cachePct = ratio * PERCENTAGE_MULTIPLIER
            appendLine(
                "- Cache Hit Ratio: ${"%.2f".format(cachePct)}% " +
                    "(${result.cacheHitCount} hits, ${result.cacheMissCount} misses)"
            )
        }
        val gcPauseMs = result.gcPauseMs
        if (gcPauseMs != null) {
            appendLine("- GC Pause Duration: $gcPauseMs ms")
        } else {
            appendLine("- GC Pause Duration: (not present in dump)")
        }
        if (result.truncated) {
            appendLine("- Lists Truncated: true")
        }

        appendLine()
        append("### Longest Running Tasks (timed only):")
        if (result.longRunningTasks.isEmpty()) {
            appendLine(" none with parseable duration")
        } else {
            appendLine()
            result.longRunningTasks.forEach { task ->
                val dur = task.durationMs?.let { "${it}ms" } ?: "unknown"
                appendLine(" - ${task.taskPath} ($dur, ${task.outcome})")
            }
        }

        appendLine()
        append("### Tasks with Cache Misses:")
        if (result.tasksWithCacheMisses.isEmpty()) {
            appendLine(" none")
        } else {
            appendLine()
            result.tasksWithCacheMisses.forEach { task ->
                appendLine(" - ${task.taskPath} (${task.outcome})")
            }
        }

        appendLine()
        append("### Actionable Optimization Advice:")
        if (result.recommendations.isEmpty()) {
            appendLine(" none from available dump metrics")
        } else {
            appendLine()
            result.recommendations.forEach { appendLine(" - $it") }
        }
    }.trimEnd()
}
