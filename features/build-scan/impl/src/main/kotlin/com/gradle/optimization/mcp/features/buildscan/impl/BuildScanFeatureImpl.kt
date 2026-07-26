package com.gradle.optimization.mcp.features.buildscan.impl

import com.gradle.optimization.mcp.features.buildscan.api.BuildScanFeatureApi
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanResult
import com.gradle.optimization.mcp.features.buildscan.api.TaskExecutionSummary
import java.io.File
import java.io.IOException
import org.koin.core.annotation.Single

private const val LOW_CACHE_RATIO_THRESHOLD = 0.50
private const val HIGH_GC_PAUSE_MS_THRESHOLD = 500L
private const val PERCENTAGE_FACTOR = 100.0

@Single
class BuildScanFeatureImpl : BuildScanFeatureApi {
    override fun analyzeBuildScan(request: BuildScanRequest): BuildScanResult {
        val targetDir = File(request.projectDir)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return errorResult(
                projectDir = request.projectDir,
                failureReason = "Project directory does not exist or is not a directory: ${request.projectDir}"
            )
        }

        val buildScanUrl = request.buildScanUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (buildScanUrl != null) {
            return errorResult(
                projectDir = request.projectDir,
                failureReason = "buildScanUrl is not supported (no remote scan fetch). " +
                    "Provide a local dumpFilePath instead. Requested URL: $buildScanUrl",
                dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
            )
        }

        val dumpPath = request.dumpFilePath?.trim()?.takeIf { it.isNotEmpty() }
        if (dumpPath == null) {
            return BuildScanResult(
                projectDir = request.projectDir,
                status = BuildScanResult.STATUS_NO_DATA,
                guidance = "No usable scan source. Pass dumpFilePath to a local plain-text " +
                    "task-outcome dump. Remote buildScanUrl fetch is not implemented.",
                dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
            )
        }

        val dumpFile = File(dumpPath)
        if (!dumpFile.exists() || !dumpFile.isFile) {
            return errorResult(
                projectDir = request.projectDir,
                failureReason = "dumpFilePath does not exist or is not a readable file: $dumpPath",
                dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
            )
        }

        val rawContent = try {
            dumpFile.readText()
        } catch (e: IOException) {
            return errorResult(
                projectDir = request.projectDir,
                failureReason = "dumpFilePath is unreadable: $dumpPath (${e.message})",
                dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
            )
        }

        val allTasks = parseTaskSummaries(rawContent)
        val gcPause = parseGcPauseMs(rawContent)
        if (allTasks.isEmpty()) {
            return BuildScanResult(
                projectDir = request.projectDir,
                status = BuildScanResult.STATUS_NO_DATA,
                gcPauseMs = gcPause,
                guidance = "Dump file contained no parseable task-outcome lines.",
                dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
            )
        }

        val cacheHits = allTasks.count {
            it.outcome == "FROM-CACHE" || it.outcome == "UP-TO-DATE"
        }
        val cacheMisses = allTasks.filter { it.outcome == "EXECUTED" }
        val totalTasks = allTasks.size
        val cacheHitRatio = cacheHits.toDouble() / totalTasks.toDouble()

        val maxListed = request.maxListedTasks.coerceAtLeast(0)
        val timedTasks = allTasks.filter { it.durationMs != null }
        val longRunningAll = timedTasks.sortedByDescending { it.durationMs }
        val longRunning = longRunningAll.take(maxListed)
        val missTasks = cacheMisses.take(maxListed)
        val truncated = longRunningAll.size > longRunning.size || cacheMisses.size > missTasks.size

        return BuildScanResult(
            projectDir = request.projectDir,
            status = BuildScanResult.STATUS_OK,
            longRunningTasks = longRunning,
            tasksWithCacheMisses = missTasks,
            cacheHitRatio = cacheHitRatio,
            gcPauseMs = gcPause,
            totalTasksCount = totalTasks,
            cacheHitCount = cacheHits,
            cacheMissCount = cacheMisses.size,
            recommendations = generateRecommendations(cacheHitRatio, gcPause, cacheMisses.size),
            truncated = truncated,
            dumpFormatHint = BuildScanResult.DUMP_FORMAT_HINT
        )
    }

    private fun errorResult(
        projectDir: String,
        failureReason: String,
        dumpFormatHint: String? = null
    ): BuildScanResult =
        BuildScanResult(
            projectDir = projectDir,
            status = BuildScanResult.STATUS_ERROR,
            failureReason = failureReason,
            dumpFormatHint = dumpFormatHint
        )

    private fun parseTaskSummaries(content: String): List<TaskExecutionSummary> {
        val summaries = mutableListOf<TaskExecutionSummary>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val match = TASK_LINE_REGEX.matchEntire(trimmed) ?: return@forEach
            val taskPath = match.groupValues[1]
            val rest = match.groupValues[2]
            val outcome = when {
                rest.contains("FROM-CACHE") -> "FROM-CACHE"
                rest.contains("UP-TO-DATE") -> "UP-TO-DATE"
                rest.contains("SKIPPED") -> "SKIPPED"
                rest.contains("FAILED") -> "FAILED"
                rest.contains("NO-SOURCE") -> "NO-SOURCE"
                rest.contains("EXECUTED") -> "EXECUTED"
                else -> "EXECUTED"
            }
            val durationMs = DURATION_REGEX.find(rest)?.groupValues?.get(1)?.toLongOrNull()
            summaries.add(
                TaskExecutionSummary(
                    taskPath = taskPath,
                    durationMs = durationMs,
                    outcome = outcome
                )
            )
        }
        return summaries
    }

    private fun parseGcPauseMs(content: String): Long? {
        val gcMatch = GC_PAUSE_REGEX.find(content) ?: return null
        return gcMatch.groupValues[1].toLongOrNull()
    }

    private fun generateRecommendations(
        ratio: Double,
        gcPauseMs: Long?,
        cacheMissCount: Int
    ): List<String> {
        val list = mutableListOf<String>()
        if (ratio < LOW_CACHE_RATIO_THRESHOLD) {
            val pctStr = "%.1f".format(ratio * PERCENTAGE_FACTOR)
            list.add("Low build cache hit ratio ($pctStr%). Audit cache key inputs for misses.")
        }
        if (gcPauseMs != null && gcPauseMs > HIGH_GC_PAUSE_MS_THRESHOLD) {
            list.add("High GC pause duration ($gcPauseMs ms). Increase Gradle Daemon max heap (-Xmx).")
        }
        if (cacheMissCount > 0) {
            list.add("$cacheMissCount task(s) executed without cache. Review output caching declarations.")
        }
        return list
    }

    private companion object {
        val TASK_LINE_REGEX = Regex("""^(:[A-Za-z0-9_.:-]+)\s*(.*)$""")
        val DURATION_REGEX = Regex("""(?i)(\d+)\s*ms""")
        val GC_PAUSE_REGEX = Regex("""GC pause:\s*(\d+)\s*ms""", RegexOption.IGNORE_CASE)
    }
}
