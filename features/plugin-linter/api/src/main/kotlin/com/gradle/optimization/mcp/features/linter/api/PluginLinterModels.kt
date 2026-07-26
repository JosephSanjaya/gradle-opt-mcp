package com.gradle.optimization.mcp.features.linter.api

import kotlinx.serialization.Serializable

@Serializable
data class PluginLinterRequest(
    val projectDir: String,
    val maxFindings: Int = DEFAULT_MAX_FINDINGS
) {
    companion object {
        const val DEFAULT_MAX_FINDINGS = 50
    }
}

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
    val totalViolations: Int,
    val violations: List<LinterViolation> = emptyList(),
    val truncated: Boolean = false,
    val rulesChecked: List<String> = emptyList(),
    val scannedRoots: List<String> = emptyList(),
    val summary: String
)
