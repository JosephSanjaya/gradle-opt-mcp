package com.gradle.optimization.mcp.features.dependencyinsight.api

import kotlinx.serialization.Serializable

@Serializable
data class DependencyInsightResult(
    val projectDir: String,
    val modulePath: String,
    val configuration: String,
    val dependency: String,
    val found: Boolean,
    val selectedVersion: String? = null,
    val reasons: List<String> = emptyList(),
    val paths: List<String> = emptyList(),
    val failureReason: String? = null
)
