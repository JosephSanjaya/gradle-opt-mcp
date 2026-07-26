package com.gradle.optimization.mcp.features.dependencygraph.api

import kotlinx.serialization.Serializable

@Serializable
data class SelectionReasonInfo(
    val descriptions: List<String> = emptyList(),
    val conflictResolution: Boolean = false,
    val constrained: Boolean = false,
    val forced: Boolean = false,
    val expected: Boolean = false
) {
    fun formatCompact(): String {
        val flags = buildList {
            if (conflictResolution) add("conflictResolution")
            if (constrained) add("constrained")
            if (forced) add("forced")
            if (expected) add("expected")
        }
        val desc = descriptions.take(MAX_DESCRIPTIONS_IN_COMPACT).joinToString("; ")
        return when {
            flags.isEmpty() && desc.isEmpty() -> "requested"
            flags.isEmpty() -> desc
            desc.isEmpty() -> flags.joinToString(",")
            else -> "${flags.joinToString(",")}: $desc"
        }
    }

    companion object {
        private const val MAX_DESCRIPTIONS_IN_COMPACT = 3
    }
}

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
    val selectionReason: SelectionReasonInfo = SelectionReasonInfo(),
    val componentKind: String = "module"
)

@Serializable
data class ConflictGroup(
    val group: String,
    val name: String,
    val resolvedVersion: String,
    val requestedVersions: List<String>,
    val modules: List<String>,
    val configurations: List<String>,
    val selectionReason: SelectionReasonInfo = SelectionReasonInfo()
)

@Serializable
data class GradleDepsResult(
    val projectDir: String,
    val configurationsScanned: List<String>,
    val totalDependencies: Int,
    val directCount: Int,
    val transitiveCount: Int,
    val conflictCount: Int,
    val conflictGroupCount: Int,
    val modulesAnalyzed: List<String>,
    val conflicts: List<ConflictGroup>,
    val dependencies: List<DependencyNode>,
    val truncated: Boolean,
    val summary: String,
    val errors: List<String> = emptyList()
)
