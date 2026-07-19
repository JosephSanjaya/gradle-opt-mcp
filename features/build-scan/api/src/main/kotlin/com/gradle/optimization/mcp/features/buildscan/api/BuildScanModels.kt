package com.gradle.optimization.mcp.features.buildscan.api

import kotlinx.serialization.Serializable

@Serializable
data class BuildScanRequest(
    val projectDir: String,
    val buildScanUrl: String? = null,
    val dumpFilePath: String? = null
)

@Serializable
data class TaskExecutionSummary(
    val taskPath: String,
    val durationMs: Long,
    val outcome: String
)

@Serializable
data class BuildScanResult(
    val projectDir: String,
    val longRunningTasks: List<TaskExecutionSummary>,
    val tasksWithCacheMisses: List<TaskExecutionSummary>,
    val cacheHitRatio: Double,
    val gcPauseMs: Long,
    val totalTasksCount: Int,
    val cacheHitCount: Int,
    val cacheMissCount: Int,
    val recommendations: List<String>,
    val rawSummary: String
)
