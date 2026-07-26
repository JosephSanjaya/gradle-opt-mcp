package com.gradle.optimization.mcp.features.runner.impl

import com.gradle.optimization.mcp.features.runner.api.GradleRunLogRequest
import com.gradle.optimization.mcp.features.runner.api.GradleRunLogResult
import java.io.File
import java.util.UUID

object BuildLogStore {
    private val ansiRegex = Regex("""\u001B\[[0-9;]*[a-zA-Z]""")
    private const val MAX_RUN_FILES = 20

    @Suppress("MagicNumber")
    fun generateRunId(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = UUID.randomUUID().toString().take(4)
        return "run_${timestamp}_$randomSuffix"
    }

    fun cleanAndDeduplicate(rawOutput: String): List<String> {
        val lines = rawOutput.lines()
        val cleaned = mutableListOf<String>()
        var lastLine: String? = null
        var repeatCount = 1

        for (line in lines) {
            val stripped = line.replace(ansiRegex, "").trimEnd()
            if (stripped.isEmpty() && lastLine?.isEmpty() == true) continue

            if (stripped == lastLine) {
                repeatCount++
            } else {
                if (lastLine != null) {
                    val entry = if (repeatCount > 1) "$lastLine [x$repeatCount]" else lastLine
                    cleaned.add(entry)
                }
                lastLine = stripped
                repeatCount = 1
            }
        }

        if (lastLine != null) {
            val entry = if (repeatCount > 1) "$lastLine [x$repeatCount]" else lastLine
            cleaned.add(entry)
        }

        return cleaned
    }

    fun saveLog(projectDir: File, runId: String, lines: List<String>) {
        val runsDir = File(projectDir, ".gradle/mcp/runs")
        if (!runsDir.exists()) {
            runsDir.mkdirs()
        }

        val logFile = File(runsDir, "$runId.log")
        logFile.writeText(lines.joinToString("\n"))

        pruneOldLogs(runsDir)
    }

    fun readLog(projectDir: File, request: GradleRunLogRequest): GradleRunLogResult {
        val runsDir = File(projectDir, ".gradle/mcp/runs")
        val logFile = File(runsDir, "${request.runId}.log")
        require(logFile.exists()) { "Build log runId not found: ${request.runId}" }

        var lines = logFile.readLines()
        val queryFilter = request.filter
        if (!queryFilter.isNullOrBlank()) {
            lines = when {
                queryFilter.equals("failure", ignoreCase = true) ->
                    SourceErrorExtractor.failureExcerpt(lines, maxLines = 80)
                else -> lines.filter { it.contains(queryFilter, ignoreCase = true) }
            }
        }

        val totalLines = lines.size
        val start = request.offset.coerceIn(0, totalLines)
        val end = (start + request.limit).coerceAtMost(totalLines)
        val pagedLines = lines.subList(start, end)
        val hasMore = end < totalLines

        return GradleRunLogResult(
            runId = request.runId,
            totalLines = totalLines,
            offset = start,
            limit = request.limit,
            lines = pagedLines,
            hasMore = hasMore
        )
    }

    private fun pruneOldLogs(runsDir: File) {
        val files = runsDir.listFiles { _, name -> name.endsWith(".log") } ?: return
        if (files.size > MAX_RUN_FILES) {
            val sorted = files.sortedBy { it.lastModified() }
            val toDeleteCount = files.size - MAX_RUN_FILES
            sorted.take(toDeleteCount).forEach { it.delete() }
        }
    }
}
