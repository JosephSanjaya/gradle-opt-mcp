package com.gradle.optimization.mcp.features.runner.impl

import com.gradle.optimization.mcp.features.runner.api.GradleSourceError

object SourceErrorExtractor {
    private val kotlinErrorModernRegex = Regex(
        """^e:\s+(?:file://)?([^:\r\n]+):(\d+):(\d+)\s+(.+)$""",
        RegexOption.MULTILINE
    )
    private val kotlinErrorLegacyRegex = Regex(
        """^e:\s+(?:file://)?([^:\r\n]+):\s*\((\d+),\s*(\d+)\):\s*(.+)$""",
        RegexOption.MULTILINE
    )
    private val javaErrorRegex = Regex(
        """^([^\s:]+\.java):(\d+):\s*error:\s*(.+)$""",
        RegexOption.MULTILINE
    )
    private val detektViolationRegex = Regex(
        """^(\S+\.kt):(\d+):(\d+):\s+(.+)$""",
        RegexOption.MULTILINE
    )
    private val gradleScriptRegex = Regex(
        """(?:Build file|Script)\s+'([^']+)'\s+line:\s*(\d+)""",
        RegexOption.MULTILINE
    )
    private val testFailureRegex = Regex(
        """^\s*([a-zA-Z0-9_.]+)\s+>\s+(.+?)\s+FAILED$""",
        RegexOption.MULTILINE
    )
    private val taskNotFoundRegex = Regex(
        """Task\s+'([^']+)'\s+not found""",
        RegexOption.MULTILINE
    )

    @Suppress("MagicNumber")
    fun extractErrors(output: String): List<GradleSourceError> {
        val errors = mutableListOf<GradleSourceError>()

        fun addKotlin(match: MatchResult) {
            errors.add(
                GradleSourceError(
                    file = match.groupValues[1].trim(),
                    line = match.groupValues[2].toIntOrNull(),
                    column = match.groupValues[3].toIntOrNull(),
                    message = match.groupValues[4].trim(),
                    errorType = "KOTLIN_COMPILER_ERROR"
                )
            )
        }

        kotlinErrorModernRegex.findAll(output).forEach(::addKotlin)
        kotlinErrorLegacyRegex.findAll(output).forEach(::addKotlin)

        javaErrorRegex.findAll(output).forEach { match ->
            errors.add(
                GradleSourceError(
                    file = match.groupValues[1].trim(),
                    line = match.groupValues[2].toIntOrNull(),
                    message = match.groupValues[3].trim(),
                    errorType = "JAVA_COMPILER_ERROR"
                )
            )
        }

        detektViolationRegex.findAll(output).forEach { match ->
            errors.add(
                GradleSourceError(
                    file = match.groupValues[1].trim(),
                    line = match.groupValues[2].toIntOrNull(),
                    column = match.groupValues[3].toIntOrNull(),
                    message = match.groupValues[4].trim(),
                    errorType = "DETEKT_VIOLATION"
                )
            )
        }

        gradleScriptRegex.findAll(output).forEach { match ->
            errors.add(
                GradleSourceError(
                    file = match.groupValues[1].trim(),
                    line = match.groupValues[2].toIntOrNull(),
                    message = "Gradle build script execution error",
                    errorType = "BUILD_SCRIPT_ERROR"
                )
            )
        }

        testFailureRegex.findAll(output).forEach { match ->
            val className = match.groupValues[1].trim()
            val methodName = match.groupValues[2].trim()
            errors.add(
                GradleSourceError(
                    message = "Test failed: $className.$methodName",
                    task = methodName,
                    errorType = "TEST_FAILURE"
                )
            )
        }

        taskNotFoundRegex.findAll(output).forEach { match ->
            val task = match.groupValues[1].trim()
            errors.add(
                GradleSourceError(
                    message = "Task '$task' not found",
                    task = task,
                    errorType = "TASK_NOT_FOUND"
                )
            )
        }

        return errors.distinctBy { Triple(it.file, it.line, it.message) }
    }

    private const val FAILURE_REASON_MAX_CHARS = 1200
    private const val FAILURE_REASON_MAX_LINES = 12
    private const val DEFAULT_EXCERPT_LINES = 40

    fun extractFailureReason(output: String): String? {
        val marker = "* What went wrong:"
        val start = output.indexOf(marker)
        if (start < 0) return null

        val after = output.substring(start + marker.length).trimStart()
        val endMarkers = listOf("* Try:", "* Exception is:", "BUILD FAILED")
        val end = endMarkers
            .map { after.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: after.length.coerceAtMost(FAILURE_REASON_MAX_CHARS)

        return after.take(end)
            .trim()
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .take(FAILURE_REASON_MAX_LINES)
            .joinToString("\n")
            .ifBlank { null }
    }

    fun failureExcerpt(cleanedLines: List<String>, maxLines: Int = DEFAULT_EXCERPT_LINES): List<String> {
        if (cleanedLines.isEmpty()) return emptyList()

        val preferredAnchors = listOf("* What went wrong:", "What went wrong:")
        val fallbackAnchors = listOf("FAILURE:", "BUILD FAILED")
        val preferredIndex = cleanedLines.indexOfFirst { line ->
            preferredAnchors.any { anchor -> line.contains(anchor) }
        }
        val anchorIndex = if (preferredIndex >= 0) {
            preferredIndex
        } else {
            cleanedLines.indexOfFirst { line ->
                fallbackAnchors.any { anchor -> line.contains(anchor) }
            }
        }

        return if (anchorIndex >= 0) {
            val start = (anchorIndex - 1).coerceAtLeast(0)
            cleanedLines.subList(start, cleanedLines.size.coerceAtMost(start + maxLines))
        } else {
            cleanedLines.takeLast(maxLines.coerceAtMost(cleanedLines.size))
        }
    }
}
