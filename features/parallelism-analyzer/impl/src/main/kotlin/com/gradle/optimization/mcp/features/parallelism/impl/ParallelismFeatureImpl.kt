package com.gradle.optimization.mcp.features.parallelism.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.parallelism.api.CriticalPathNode
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisRequest
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisResult
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

private const val MAX_PARALLELIZABLE_FRACTION = 0.95
private const val PERCENTAGE_MULTIPLIER = 100.0
private const val TOP_BOTTLENECK_LIMIT = 5

@Single
class ParallelismFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ParallelismFeatureApi {
    override fun analyzeParallelizationBottlenecks(request: ParallelismAnalysisRequest): ParallelismAnalysisResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Project directory does not exist: ${request.projectDir}" }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        pool.withConnection(targetDir) { connection ->
            val launcher = connection.newBuild()
            val taskList = request.tasks.ifEmpty { listOf("build") }
            launcher.forTasks(*taskList.toTypedArray())
            launcher.withArguments("--dry-run", "--console=plain")
            launcher.setStandardOutput(stdout)
            launcher.setStandardError(stderr)
            launcher.run()
        }

        val rawOutput = stdout.toString(Charsets.UTF_8)
        val taskPaths = parseTaskPaths(rawOutput)

        if (taskPaths.isEmpty()) {
            return ParallelismAnalysisResult(
                projectDir = request.projectDir,
                maxThreads = request.maxThreads,
                totalTasks = 0,
                criticalPathLength = 0,
                parallelizableFraction = 0.0,
                theoreticalSpeedup = 1.0,
                criticalPath = emptyList(),
                bottleneckModules = emptyList(),
                summary = "No tasks executed in dry run for ${request.projectDir}"
            )
        }

        val criticalPathNodes = calculateCriticalPath(taskPaths)
        val totalTasks = taskPaths.size
        val criticalLength = criticalPathNodes.size

        val pRaw = if (totalTasks > 0) {
            (totalTasks - criticalLength).toDouble() / totalTasks
        } else {
            0.0
        }
        val boundedPFraction = pRaw.coerceIn(0.0, MAX_PARALLELIZABLE_FRACTION)
        val parallelizableFraction = (boundedPFraction * PERCENTAGE_MULTIPLIER).roundToInt() / PERCENTAGE_MULTIPLIER
        val n = request.maxThreads.coerceAtLeast(1)
        val denominator = (1.0 - parallelizableFraction) + (parallelizableFraction / n)
        val speedupRaw = if (denominator > 0.0) 1.0 / denominator else 1.0
        val theoreticalSpeedup = (speedupRaw * PERCENTAGE_MULTIPLIER).roundToInt() / PERCENTAGE_MULTIPLIER

        val projectCounts = criticalPathNodes.groupingBy { it.projectPath }.eachCount()
        val bottleneckModules = projectCounts.entries
            .sortedByDescending { it.value }
            .take(TOP_BOTTLENECK_LIMIT)
            .map { it.key }

        val parallelPercent = (parallelizableFraction * PERCENTAGE_MULTIPLIER).roundToInt()
        val summary = buildString {
            append("Amdahl's Law Speedup Analysis ($n threads): ")
            append("Theoretical speedup is ${theoreticalSpeedup}x with ")
            append("$parallelPercent% parallelizable work fraction. ")
            append("Critical execution path length: $criticalLength tasks across ")
            append("${bottleneckModules.size} primary bottleneck module(s).")
        }

        return ParallelismAnalysisResult(
            projectDir = request.projectDir,
            maxThreads = request.maxThreads,
            totalTasks = totalTasks,
            criticalPathLength = criticalLength,
            parallelizableFraction = parallelizableFraction,
            theoreticalSpeedup = theoreticalSpeedup,
            criticalPath = criticalPathNodes,
            bottleneckModules = bottleneckModules,
            summary = summary
        )
    }

    private fun parseTaskPaths(output: String): List<String> {
        val paths = mutableListOf<String>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(":")) {
                val taskPath = trimmed.split("\\s+".toRegex()).firstOrNull()
                if (taskPath != null) {
                    paths.add(taskPath)
                }
            }
        }
        return paths
    }

    private fun calculateCriticalPath(taskPaths: List<String>): List<CriticalPathNode> {
        val criticalTasks = taskPaths.filter { path ->
            path.endsWith(":compileKotlin") ||
                path.endsWith(":compileJava") ||
                path.endsWith(":jar") ||
                path.endsWith(":classes") ||
                path.endsWith(":build") ||
                path.endsWith(":check")
        }.ifEmpty { taskPaths }

        return criticalTasks.map { taskPath ->
            val lastColon = taskPath.lastIndexOf(':')
            val projectPath = if (lastColon > 0) taskPath.substring(0, lastColon) else ":"
            CriticalPathNode(
                taskPath = taskPath,
                projectPath = projectPath,
                estimatedWeight = 1
            )
        }
    }
}
