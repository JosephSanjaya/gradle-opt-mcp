package com.gradle.optimization.mcp.features.dependencyinsight.api

import kotlinx.serialization.Serializable

@Serializable
data class DependencyInsightRequest(
    val projectDir: String,
    val configuration: String,
    val dependency: String,
    val modulePath: String = DEFAULT_MODULE_PATH,
    val maxPaths: Int = DEFAULT_MAX_PATHS,
    val maxReasons: Int = DEFAULT_MAX_REASONS
) {
    companion object {
        const val DEFAULT_MODULE_PATH = ":"
        const val DEFAULT_MAX_PATHS = 20
        const val DEFAULT_MAX_REASONS = 20
    }
}
