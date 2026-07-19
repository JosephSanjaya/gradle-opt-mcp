package com.gradle.optimization.mcp.features.dependencyinsight.api

data class DependencyInsightResult(
    val projectDir: String,
    val configuration: String,
    val dependency: String,
    val insightOutput: String
)
