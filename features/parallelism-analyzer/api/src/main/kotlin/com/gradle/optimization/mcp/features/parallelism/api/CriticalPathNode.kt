package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

@Serializable
data class CriticalPathNode(
    val taskPath: String,
    val projectPath: String,
    val estimatedWeight: Int = 1
)
