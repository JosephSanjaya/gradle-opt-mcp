package com.gradle.optimization.mcp.server.tools

import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisRequest
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismFeatureApi
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private const val DEFAULT_MAX_THREADS = 8
private const val CRITICAL_PATH_SAMPLE_LIMIT = 10

@Single
class ParallelismToolsRegistrar(
    @Provided private val parallelismApi: ParallelismFeatureApi
) : McpToolsRegistrar {
    override fun register(server: Server) {
        server.addTool(
            name = "analyze_parallelization_bottlenecks",
            description = "Apply Amdahl's Law calculations to detect sequential bottlenecks, " +
                "critical execution paths, and theoretical speedup in multi-module builds.",
            inputSchema = ToolSchema(
                properties = kotlinx.serialization.json.buildJsonObject {
                    put("projectDir", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
                    put("maxThreads", kotlinx.serialization.json.buildJsonObject { put("type", "integer") })
                    put(
                        "tasks",
                        kotlinx.serialization.json.buildJsonObject {
                            put("type", "array")
                            put("items", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
                        }
                    )
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
            val maxThreads = args["maxThreads"]?.jsonPrimitive?.intOrNull ?: DEFAULT_MAX_THREADS
            val tasks = args["tasks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

            val result = parallelismApi.analyzeParallelizationBottlenecks(
                ParallelismAnalysisRequest(
                    projectDir = projectDir,
                    tasks = tasks,
                    maxThreads = maxThreads
                )
            )

            val text = buildString {
                appendLine("Summary: ${result.summary}")
                appendLine("Total Executed Tasks: ${result.totalTasks}")
                appendLine("Critical Execution Path Length: ${result.criticalPathLength}")
                appendLine("Parallelizable Fraction (P): ${result.parallelizableFraction}")
                appendLine("Theoretical Speedup (${result.maxThreads} threads): ${result.theoreticalSpeedup}x")
                if (result.bottleneckModules.isNotEmpty()) {
                    appendLine("\nBottleneck Modules:")
                    result.bottleneckModules.forEach { appendLine("- $it") }
                }
                if (result.criticalPath.isNotEmpty()) {
                    appendLine("\nCritical Path Sample:")
                    result.criticalPath.take(CRITICAL_PATH_SAMPLE_LIMIT).forEach { appendLine("- ${it.taskPath}") }
                }
            }
            CallToolResult(content = listOf(TextContent(text = text.trimEnd())))
        }
    }
}
