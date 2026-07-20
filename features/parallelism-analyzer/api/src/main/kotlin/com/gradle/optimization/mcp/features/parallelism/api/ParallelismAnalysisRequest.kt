package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

@Serializable
data class ParallelismAnalysisRequest(
    val projectDir: String,
    val tasks: List<String> = emptyList(),
    val maxThreads: Int = 8
)
