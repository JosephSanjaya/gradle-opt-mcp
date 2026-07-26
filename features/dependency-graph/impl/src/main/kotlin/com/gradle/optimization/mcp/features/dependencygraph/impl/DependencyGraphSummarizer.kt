package com.gradle.optimization.mcp.features.dependencygraph.impl

import com.gradle.optimization.mcp.features.dependencygraph.api.ConflictGroup
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyNode
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsResult

internal object DependencyGraphSummarizer {
    fun summarize(
        projectDir: String,
        nodes: List<DependencyNode>,
        configurationsScanned: List<String>,
        request: GradleDepsRequest,
        errors: List<String>
    ): GradleDepsResult {
        val filtered = nodes.filter { node ->
            val transitiveOk = request.includeTransitive || node.isDirect
            val conflictOk = !request.onlyConflicts || node.hasConflict
            transitiveOk && conflictOk
        }

        val directCount = filtered.count { it.isDirect }
        val transitiveCount = filtered.size - directCount
        val conflictNodes = filtered.filter { it.hasConflict }
        val conflictGroups = groupConflicts(conflictNodes)
        val maxDeps = request.maxDependencies.coerceAtLeast(0)
        val maxConflicts = request.maxConflicts.coerceAtLeast(0)

        val cappedConflicts = conflictGroups.take(maxConflicts)
        val preferred = if (request.onlyConflicts) {
            conflictNodes
        } else {
            filtered.filter { it.isDirect }.ifEmpty { filtered }
        }
        val cappedDeps = preferred.take(maxDeps)

        val truncated =
            cappedConflicts.size < conflictGroups.size ||
                cappedDeps.size < preferred.size ||
                (!request.includeTransitive && transitiveCount > 0 && !request.onlyConflicts)

        val modulesAnalyzed = filtered.map { it.modulePath }.distinct().sorted()
        val summary = buildSummary(
            total = filtered.size,
            directCount = directCount,
            transitiveCount = transitiveCount,
            conflictGroupCount = conflictGroups.size,
            configs = configurationsScanned,
            truncated = truncated,
            includeTransitive = request.includeTransitive
        )

        return GradleDepsResult(
            projectDir = projectDir,
            configurationsScanned = configurationsScanned,
            totalDependencies = filtered.size,
            directCount = directCount,
            transitiveCount = transitiveCount,
            conflictCount = conflictNodes.size,
            conflictGroupCount = conflictGroups.size,
            modulesAnalyzed = modulesAnalyzed,
            conflicts = cappedConflicts,
            dependencies = cappedDeps,
            truncated = truncated,
            summary = summary,
            errors = errors
        )
    }

    fun groupConflicts(conflictNodes: List<DependencyNode>): List<ConflictGroup> {
        return conflictNodes
            .groupBy { "${it.group}:${it.name}" }
            .map { (_, groupNodes) ->
                val first = groupNodes.first()
                ConflictGroup(
                    group = first.group,
                    name = first.name,
                    resolvedVersion = first.resolvedVersion,
                    requestedVersions = groupNodes.map { it.requestedVersion }.distinct().sorted(),
                    modules = groupNodes.map { it.modulePath }.distinct().sorted(),
                    configurations = groupNodes.map { it.configuration }.distinct().sorted(),
                    selectionReason = first.selectionReason
                )
            }
            .sortedWith(
                compareByDescending<ConflictGroup> { it.requestedVersions.size }
                    .thenBy { "${it.group}:${it.name}" }
            )
    }

    private fun buildSummary(
        total: Int,
        directCount: Int,
        transitiveCount: Int,
        conflictGroupCount: Int,
        configs: List<String>,
        truncated: Boolean,
        includeTransitive: Boolean
    ): String {
        val configLabel = if (configs.isEmpty()) "none" else configs.joinToString(", ")
        val trunc = if (truncated) "; truncated=true" else ""
        val transitiveNote = if (!includeTransitive && transitiveCount > 0) {
            " (transitive omitted from list; set includeTransitive=true)"
        } else {
            ""
        }
        return "deps=$total (direct=$directCount, transitive=$transitiveCount); " +
            "conflictGroups=$conflictGroupCount; configs=[$configLabel]$trunc$transitiveNote"
    }
}
