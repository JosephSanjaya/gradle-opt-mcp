package com.gradle.optimization.mcp.features.isolation.api

import kotlinx.serialization.Serializable

@Serializable
data class IsolationCheckRequest(
    val projectDir: String,
    val recreateCache: Boolean = false,
    val maxViolations: Int = DEFAULT_MAX_VIOLATIONS
) {
    companion object {
        const val DEFAULT_MAX_VIOLATIONS = 40
    }
}

@Serializable
data class IsolationViolation(
    val message: String,
    val violationType: String,
    val location: String? = null,
    val sourceFile: String? = null,
    val lineNumber: Int? = null,
    val documentationLink: String? = null
)

@Serializable
data class IsolationCheckResult(
    val projectDir: String,
    val success: Boolean,
    val isIsolated: Boolean,
    val totalViolationCount: Int,
    val violations: List<IsolationViolation>,
    val htmlReportPath: String? = null,
    val failureReason: String? = null,
    val summary: String
)
