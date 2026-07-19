package com.gradle.optimization.mcp.features.dependencyinsight.api

data class DependencyInsightRequest(
    val projectDir: String,
    val configuration: String,
    val dependency: String
)
