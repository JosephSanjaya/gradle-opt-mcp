package com.gradle.optimization.mcp.features.linter.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginLinterRequest(
    val projectDir: String
)

@Serializable
data class LinterViolation(
    val file: String,
    val line: Int,
    val ruleId: String,
    val category: String,
    val message: String,
    val recommendation: String,
    val snippet: String
)

@Serializable
data class PluginLinterResult(
    val projectDir: String,
    val scannedFilesCount: Int,
    val violations: List<LinterViolation> = emptyList(),
    val summary: String
)
