package com.gradle.optimization.mcp.features.dependencygraph.api

import kotlinx.serialization.Serializable

@Serializable
data class DependencyNode(
    val modulePath: String,
    val configuration: String,
    val group: String,
    val name: String,
    val requestedVersion: String,
    val resolvedVersion: String,
    val isDirect: Boolean,
    val isTransitive: Boolean,
    val hasConflict: Boolean,
    val selectionReason: String
)

@Serializable
data class GradleDepsResult(
    val projectDir: String,
    val totalDependencies: Int,
    val conflictCount: Int,
    val modulesAnalyzed: List<String>,
    val dependencies: List<DependencyNode>,
    val errors: List<String> = emptyList()
)
