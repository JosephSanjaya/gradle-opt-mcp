package com.gradle.optimization.mcp.features.dependencygraph.api

import kotlinx.serialization.Serializable

@Serializable
data class GradleDepsRequest(
    val projectDir: String,
    val modulePath: String? = null,
    val configuration: String? = null,
    val includeTransitive: Boolean = false,
    val onlyConflicts: Boolean = false,
    val maxDependencies: Int = DEFAULT_MAX_DEPENDENCIES,
    val maxConflicts: Int = DEFAULT_MAX_CONFLICTS
) {
    companion object {
        const val DEFAULT_MAX_DEPENDENCIES = 40
        const val DEFAULT_MAX_CONFLICTS = 20

        /** Used when [configuration] is null. Pass `"all"` to scan every resolvable configuration. */
        val DEFAULT_CONFIGURATION_ALLOWLIST: List<String> = listOf(
            "compileClasspath",
            "runtimeClasspath"
        )
    }
}
