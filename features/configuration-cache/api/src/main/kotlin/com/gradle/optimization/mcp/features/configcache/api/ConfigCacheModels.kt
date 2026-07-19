package com.gradle.optimization.mcp.features.configcache.api

import kotlinx.serialization.Serializable

@Serializable
data class ConfigCacheAuditRequest(
    val projectDir: String,
    val tasks: List<String> = listOf("help")
)

@Serializable
data class ConfigCacheInputViolation(
    val inputName: String,
    val inputType: String,
    val location: String? = null,
    val antiPattern: String,
    val recommendedRefactoring: String
)

@Serializable
data class ConfigCacheAuditResult(
    val projectDir: String,
    val cacheHit: Boolean,
    val inputsAudited: List<ConfigCacheInputViolation>,
    val htmlReportPath: String? = null,
    val summary: String
)
