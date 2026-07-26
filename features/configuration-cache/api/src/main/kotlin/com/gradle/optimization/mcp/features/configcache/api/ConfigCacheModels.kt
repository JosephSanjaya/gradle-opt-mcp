package com.gradle.optimization.mcp.features.configcache.api

import kotlinx.serialization.Serializable

@Serializable
data class ConfigCacheAuditRequest(
    val projectDir: String,
    val tasks: List<String> = listOf("help"),
    val maxNotableInputs: Int = DEFAULT_MAX_NOTABLE_INPUTS
) {
    companion object {
        const val DEFAULT_MAX_NOTABLE_INPUTS = 40
    }
}

@Serializable
data class ConfigCacheInputViolation(
    val inputName: String,
    val inputType: String,
    val location: String? = null,
    val antiPattern: String? = null,
    val recommendedRefactoring: String? = null,
    val documentationLink: String? = null
)

@Serializable
data class ConfigCacheInputTypeCount(
    val inputType: String,
    val count: Int
)

@Serializable
data class ConfigCacheProblem(
    val message: String,
    val location: String? = null,
    val documentationLink: String? = null
)

@Serializable
data class ConfigCacheAuditResult(
    val projectDir: String,
    val success: Boolean,
    val cacheHit: Boolean,
    val cacheAction: String? = null,
    val requestedTasks: List<String> = emptyList(),
    val totalInputs: Int = 0,
    val inputCounts: List<ConfigCacheInputTypeCount> = emptyList(),
    val notableInputs: List<ConfigCacheInputViolation> = emptyList(),
    val totalProblemCount: Int = 0,
    val problems: List<ConfigCacheProblem> = emptyList(),
    val htmlReportPath: String? = null,
    val failureReason: String? = null,
    val summary: String
)
