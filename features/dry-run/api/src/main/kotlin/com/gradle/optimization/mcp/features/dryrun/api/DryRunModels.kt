package com.gradle.optimization.mcp.features.dryrun.api

import kotlinx.serialization.Serializable

@Serializable
data class DryRunRequest(
    val projectDir: String,
    val tasks: List<String> = listOf("build")
)

@Serializable
data class DryRunCount(
    val name: String,
    val count: Int
)

@Serializable
data class DryRunTaskNode(
    val taskPath: String,
    val status: String = STATUS_SCHEDULED
) {
    companion object {
        const val STATUS_SCHEDULED = "SCHEDULED"
    }
}

@Serializable
data class DryRunResult(
    val projectDir: String,
    val success: Boolean,
    val requestedTasks: List<String>,
    val taskCount: Int,
    val byModule: List<DryRunCount>,
    val byLeafType: List<DryRunCount>,
    val sampleTasks: List<DryRunTaskNode>,
    val failureReason: String? = null,
    val summary: String
)
