package com.gradle.optimization.mcp.features.dependencygraph.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleDepsRequest(
    val projectDir: String? = null,
    val modulePath: String? = null,
    val configuration: String? = null,
    val includeTransitive: Boolean = true,
    val onlyConflicts: Boolean = false
)
