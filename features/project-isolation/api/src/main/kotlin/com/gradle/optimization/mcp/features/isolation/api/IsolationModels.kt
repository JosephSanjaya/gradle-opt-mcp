package com.gradle.optimization.mcp.features.isolation.api

import kotlinx.serialization.Serializable

@Serializable
data class IsolationCheckRequest(
    val projectDir: String
)

@Serializable
data class IsolationViolation(
    val sourceFile: String? = null,
    val lineNumber: Int? = null,
    val message: String,
    val violationType: String
)

@Serializable
data class IsolationCheckResult(
    val projectDir: String,
    val isIsolated: Boolean,
    val violations: List<IsolationViolation>
)
