package com.gradle.optimization.mcp.features.buildscan.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanFeatureApi
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanResult
import com.gradle.optimization.mcp.features.buildscan.api.TaskExecutionSummary
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

private const val DEFAULT_TASK_DURATION_MS = 150L
private const val LOW_CACHE_RATIO_THRESHOLD = 0.50
private const val HIGH_GC_PAUSE_MS_THRESHOLD = 500L
private const val TOP_TASKS_LIMIT = 5
private const val PERCENTAGE_FACTOR = 100.0

@Single
class BuildScanFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : BuildScanFeatureApi {
    override fun analyzeBuildScan(request: BuildScanRequest): BuildScanResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Project directory does not exist: ${request.projectDir}" }

        val dumpFile = request.dumpFilePath?.let { File(it) }
        val rawContent = if (dumpFile != null && dumpFile.exists()) {
            dumpFile.readText()
        } else {
            val stdout = ByteArrayOutputStream()
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks("help")
                launcher.setStandardOutput(stdout)
                launcher.run()
            }
            stdout.toString(Charsets.UTF_8)
        }

        val allTasks = parseTaskSummaries(rawContent)
        val cacheHits = allTasks.count { it.outcome == "FROM-CACHE" || it.outcome == "UP-TO-DATE" }
        val cacheMisses = allTasks.count { it.outcome == "EXECUTED" }
        val totalTasks = allTasks.size.coerceAtLeast(1)
        val cacheHitRatio = (cacheHits.toDouble() / totalTasks.toDouble()).coerceIn(0.0, 1.0)
        val gcPause = parseGcPauseMs(rawContent)

        val longRunning = allTasks.sortedByDescending { it.durationMs }.take(TOP_TASKS_LIMIT)
        val missTasks = allTasks.filter { it.outcome == "EXECUTED" }

        val recommendations = generateRecommendations(cacheHitRatio, gcPause, missTasks)

        return BuildScanResult(
            projectDir = request.projectDir,
            longRunningTasks = longRunning,
            tasksWithCacheMisses = missTasks,
            cacheHitRatio = cacheHitRatio,
            gcPauseMs = gcPause,
            totalTasksCount = totalTasks,
            cacheHitCount = cacheHits,
            cacheMissCount = cacheMisses,
            recommendations = recommendations,
            rawSummary = rawContent
        )
    }

    private fun parseTaskSummaries(content: String): List<TaskExecutionSummary> {
        val summaries = mutableListOf<TaskExecutionSummary>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith(":")) {
                val parts = trimmed.split("\\s+".toRegex())
                val taskPath = parts.firstOrNull() ?: trimmed
                val outcome = if (trimmed.contains("FROM-CACHE")) {
                    "FROM-CACHE"
                } else if (trimmed.contains("UP-TO-DATE")) {
                    "UP-TO-DATE"
                } else {
                    "EXECUTED"
                }
                summaries.add(
                    TaskExecutionSummary(
                        taskPath = taskPath,
                        durationMs = DEFAULT_TASK_DURATION_MS,
                        outcome = outcome
                    )
                )
            }
        }
        return summaries
    }

    private fun parseGcPauseMs(content: String): Long {
        val gcMatch = Regex("GC pause:\\s*(\\d+)ms").find(content)
        return gcMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }

    private fun generateRecommendations(
        ratio: Double,
        gcPauseMs: Long,
        cacheMisses: List<TaskExecutionSummary>
    ): List<String> {
        val list = mutableListOf<String>()
        if (ratio < LOW_CACHE_RATIO_THRESHOLD) {
            val pctStr = "%.1f".format(ratio * PERCENTAGE_FACTOR)
            list.add("Low build cache hit ratio ($pctStr%). Audit cache key inputs for misses.")
        }
        if (gcPauseMs > HIGH_GC_PAUSE_MS_THRESHOLD) {
            list.add("High GC pause duration ($gcPauseMs ms). Increase Gradle Daemon max heap (-Xmx).")
        }
        if (cacheMisses.isNotEmpty()) {
            list.add("${cacheMisses.size} task(s) executed without cache. Review output caching declarations.")
        }
        if (list.isEmpty()) {
            list.add("Build scan metrics optimal. No major cache or memory bottlenecks detected.")
        }
        return list
    }
}
