package com.gradle.optimization.mcp.features.runner.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleRunRequest(
    val projectDir: String? = null,
    val tasks: List<String> = listOf("build"),
    val arguments: List<String> = emptyList()
)

@Serializable
data class TaskOutcome(
    val taskPath: String,
    val outcome: String
)

@Serializable
data class GradleSourceError(
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val message: String,
    val task: String? = null,
    val snippet: String? = null,
    val errorType: String
)

@Serializable
data class GradleRunResult(
    val runId: String? = null,
    val success: Boolean,
    val executionTimeMs: Long,
    val tasksExecuted: List<TaskOutcome> = emptyList(),
    val parsedErrors: List<GradleSourceError> = emptyList(),
    val outputSummary: String
)

@Serializable
data class GradleRunLogRequest(
    val projectDir: String? = null,
    val runId: String,
    val offset: Int = 0,
    val limit: Int = 200,
    val filter: String? = null
)

@Serializable
data class GradleRunLogResult(
    val runId: String,
    val totalLines: Int,
    val offset: Int,
    val limit: Int,
    val lines: List<String>,
    val hasMore: Boolean
)
