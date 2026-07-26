package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

@Serializable
data class ParallelismCount(
    val name: String,
    val count: Int
)

@Serializable
data class ParallelismAnalysisResult(
    val projectDir: String,
    val success: Boolean,
    val requestedTasks: List<String>,
    val analysisStatus: String,
    val taskCount: Int,
    val byModule: List<ParallelismCount>,
    val byLeafType: List<ParallelismCount>,
    val heuristicCriticalTasks: List<CriticalPathNode>,
    val heuristicCriticalTasksTruncated: Boolean,
    val bottleneckModules: List<String>,
    val caveat: String,
    val guidance: String? = null,
    val failureReason: String? = null,
    val summary: String
) {
    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_EMPTY_GRAPH = "EMPTY_GRAPH"
        const val STATUS_SHALLOW_GRAPH = "SHALLOW_GRAPH"
        const val STATUS_FAILED = "FAILED"

        const val CAVEAT =
            "Heuristic only: dry-run listing order is not a real task-dependency critical path; " +
                "no Amdahl theoretical speedup is claimed."
    }
}
