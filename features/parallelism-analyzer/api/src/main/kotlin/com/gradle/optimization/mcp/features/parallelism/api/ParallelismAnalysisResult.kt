package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

@Serializable
data class ParallelismAnalysisResult(
    val projectDir: String,
    val maxThreads: Int,
    val totalTasks: Int,
    val criticalPathLength: Int,
    val parallelizableFraction: Double,
    val theoreticalSpeedup: Double,
    val criticalPath: List<CriticalPathNode>,
    val bottleneckModules: List<String>,
    val summary: String
)
