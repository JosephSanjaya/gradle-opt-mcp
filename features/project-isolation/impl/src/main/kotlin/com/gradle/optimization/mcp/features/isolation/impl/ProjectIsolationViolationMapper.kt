package com.gradle.optimization.mcp.features.isolation.impl

import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheProblem
import com.gradle.optimization.mcp.features.isolation.api.IsolationViolation

internal object ProjectIsolationViolationMapper {
    private val buildFileLineRegex =
        Regex("""(?:Build file|file)\s+['"]([^'"]+)['"](?::\s*line\s+(\d+))?""", RegexOption.IGNORE_CASE)
    private val lineOnlyRegex = Regex(""":\s*line\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun isIncubatingProjectAccessorNoise(message: String): Boolean {
        val normalized = message.lowercase()
        if (normalized.contains("project accessor")) return true
        if (normalized.contains("type-safe project accessors")) return true
        return normalized.contains("incubating") && normalized.contains("accessor")
    }

    fun fromReportProblems(problems: List<ConfigCacheProblem>): List<IsolationViolation> =
        problems.mapNotNull { problem ->
            val message = problem.message.trim()
            if (message.isBlank() || isIncubatingProjectAccessorNoise(message)) return@mapNotNull null
            val violationType = classify(message) ?: return@mapNotNull null
            val location = problem.location
            val (sourceFile, lineNumber) = parseLocation(location)
            IsolationViolation(
                message = message,
                violationType = violationType,
                location = location,
                sourceFile = sourceFile,
                lineNumber = lineNumber,
                documentationLink = problem.documentationLink
            )
        }.distinctBy { it.message to it.location to it.violationType }

    fun fromConsoleOutput(output: String): List<IsolationViolation> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isIncubatingProjectAccessorNoise(it) }
            .mapNotNull { line ->
                val violationType = classifyConsoleLine(line) ?: return@mapNotNull null
                IsolationViolation(
                    message = line,
                    violationType = violationType
                )
            }
            .distinctBy { it.message to it.violationType }
            .toList()

    fun classify(message: String): String? {
        if (isIncubatingProjectAccessorNoise(message)) return null
        val normalized = message.lowercase()
        return when {
            normalized.contains("project.plugins") ||
                normalized.contains("project.getplugins") -> "ILLEGAL_SIBLING_PLUGIN_ACCESS"
            normalized.contains("getrootproject") ||
                normalized.contains("rootproject") -> "ILLEGAL_ROOT_PROPERTY_ACCESS"
            normalized.contains("subprojects") ||
                normalized.contains("allprojects") -> "ILLEGAL_CROSS_PROJECT_CLOSURE"
            normalized.contains("task.getproject") ||
                (normalized.contains("task action") && normalized.contains("getproject")) ||
                (
                    Regex("""\bgetproject\b""", RegexOption.IGNORE_CASE).containsMatchIn(message) &&
                        normalized.contains("task")
                    ) -> "INVALID_TASK_PROJECT_ACCESS"
            isCrossProjectIsolationProblem(normalized) -> "CROSS_PROJECT_ACCESS"
            else -> null
        }
    }

    private fun classifyConsoleLine(line: String): String? {
        classify(line)?.let { return it }
        val normalized = line.lowercase()
        if (!isCrossProjectIsolationProblem(normalized) &&
            !normalized.contains("project isolation") &&
            !normalized.contains("projectisolation")
        ) {
            return null
        }
        return "CROSS_PROJECT_ACCESS"
    }

    private fun isCrossProjectIsolationProblem(normalized: String): Boolean {
        if (normalized.contains("project isolation")) return true
        if (normalized.contains("projectisolation")) return true
        if (normalized.contains("isolated project") && normalized.contains("cannot")) return true
        if (normalized.contains("cannot access project")) return true
        if (normalized.contains("cannot access") && normalized.contains(" on project ")) return true
        return false
    }

    private fun parseLocation(location: String?): Pair<String?, Int?> {
        if (location.isNullOrBlank()) return null to null
        val match = buildFileLineRegex.find(location)
        if (match != null) {
            val file = match.groupValues[1].ifBlank { null }
            val line = match.groupValues.getOrNull(2)?.toIntOrNull()
            return file to line
        }
        val line = lineOnlyRegex.find(location)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return null to line
    }
}
