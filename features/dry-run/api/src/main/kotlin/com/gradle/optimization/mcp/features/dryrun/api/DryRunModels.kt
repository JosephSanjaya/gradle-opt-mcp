package com.gradle.optimization.mcp.features.dryrun.api

import kotlinx.serialization.Serializable

@Serializable
data class DryRunRequest(
    val projectDir: String,
    val tasks: List<String> = listOf("build")
)

@Serializable
data class DryRunTaskNode(
    val taskPath: String,
    val taskClass: String? = null,
    val skipped: Boolean = false
)

@Serializable
data class DryRunResult(
    val projectDir: String,
    val tasksExecuted: List<DryRunTaskNode>,
    val rawOutput: String
)
