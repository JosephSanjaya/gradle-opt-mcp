package com.gradle.optimization.mcp.features.buildscan.api

import kotlinx.serialization.Serializable

@Serializable
data class BuildScanRequest(
    val projectDir: String,
    val buildScanUrl: String? = null,
    val dumpFilePath: String? = null,
    val maxListedTasks: Int = DEFAULT_MAX_LISTED_TASKS
) {
    companion object {
        const val DEFAULT_MAX_LISTED_TASKS = 20
    }
}

@Serializable
data class TaskExecutionSummary(
    val taskPath: String,
    val durationMs: Long? = null,
    val outcome: String
)

@Serializable
data class BuildScanResult(
    val projectDir: String,
    val status: String,
    val failureReason: String? = null,
    val guidance: String? = null,
    val longRunningTasks: List<TaskExecutionSummary> = emptyList(),
    val tasksWithCacheMisses: List<TaskExecutionSummary> = emptyList(),
    val cacheHitRatio: Double? = null,
    val gcPauseMs: Long? = null,
    val totalTasksCount: Int = 0,
    val cacheHitCount: Int = 0,
    val cacheMissCount: Int = 0,
    val recommendations: List<String> = emptyList(),
    val truncated: Boolean = false,
    val dumpFormatHint: String? = null
) {
    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_NO_DATA = "NO_DATA"
        const val STATUS_ERROR = "ERROR"

        const val DUMP_FORMAT_HINT =
            "Supported dump: plain-text lines starting with a Gradle task path " +
                "(`:module:task`), optionally followed by outcome " +
                "(FROM-CACHE / UP-TO-DATE / SKIPPED / FAILED / EXECUTED) and optional " +
                "`Nms` duration; optional `GC pause: Nms`. " +
                "Remote buildScanUrl fetch is not supported."
    }
}
