package com.gradle.optimization.mcp.features.parallelism.api

import kotlinx.serialization.Serializable

/** Heuristic sequential-candidate node from dry-run listing (not a real dependency critical path). */
@Serializable
data class CriticalPathNode(
    val taskPath: String,
    val projectPath: String
)
