package com.gradle.optimization.mcp.features.dependencyinsight.impl

internal data class ParsedDependencyInsight(
    val found: Boolean,
    val selectedVersion: String? = null,
    val reasons: List<String> = emptyList(),
    val paths: List<String> = emptyList(),
    val notFoundMessage: String? = null
)

internal object DependencyInsightParser {
    private val reasonLine = Regex("""^\s+-\s+(.+)$""")
    private val gavHeader = Regex(
        "^(?<group>[\\w.-]+):(?<name>[\\w.-]+)" +
            "(?::(?<version>[^\\s(]+))?" +
            "(?:\\s+->\\s+(?<selected>\\S+))?" +
            "(?:\\s+\\([^)]*\\))?$"
    )
    private const val MAX_PATH_LINES = 30
    private const val FAILURE_REASON_MAX_LINES = 8

    fun parse(raw: String, maxPaths: Int, maxReasons: Int): ParsedDependencyInsight {
        val text = raw.trim()
        if (text.isEmpty()) {
            return ParsedDependencyInsight(found = false, notFoundMessage = "Empty dependency insight output")
        }

        val notFoundLine = text.lineSequence().map { it.trim() }.firstOrNull {
            it.startsWith("No dependencies matching given input were found")
        }
        if (notFoundLine != null) {
            return ParsedDependencyInsight(found = false, notFoundMessage = notFoundLine)
        }

        val lines = text.lines()
        val selectedVersion = lines.firstNotNullOfOrNull(::extractSelectedVersion)
        val reasons = extractReasons(lines).take(maxReasons.coerceAtLeast(0))
        val paths = extractPaths(lines).take(maxPaths.coerceAtLeast(0))

        val found = selectedVersion != null || reasons.isNotEmpty() || paths.isNotEmpty()
        return ParsedDependencyInsight(
            found = found,
            selectedVersion = selectedVersion,
            reasons = reasons,
            paths = paths,
            notFoundMessage = if (found) null else "No dependency insight parsed from Gradle output"
        )
    }

    fun extractBuildFailureReason(output: String): String? {
        val unknownConfig = output.lineSequence()
            .map { it.trim().removePrefix("> ").trim() }
            .firstOrNull { it.startsWith("Unknown configuration '") }
        if (unknownConfig != null) return unknownConfig

        val marker = "* What went wrong:"
        val start = output.indexOf(marker)
        if (start < 0) return null
        val after = output.substring(start + marker.length).trimStart()
        val endMarkers = listOf("* Try:", "* Exception is:", "BUILD FAILED")
        val end = endMarkers
            .map { after.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: after.length
        return after.take(end)
            .trim()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(FAILURE_REASON_MAX_LINES)
            .joinToString("\n")
            .ifBlank { null }
    }

    internal fun extractSelectedVersion(line: String): String? {
        val match = gavHeader.matchEntire(line.trim()) ?: return null
        val arrowVersion = match.groups["selected"]?.value?.takeIf { it.isNotBlank() }
        val declaredVersion = match.groups["version"]?.value?.takeIf { it.isNotBlank() }
        val selected = arrowVersion ?: declaredVersion ?: return null
        return selected.takeIf { !it.startsWith('{') }
    }

    private fun extractReasons(lines: List<String>): List<String> {
        val reasons = mutableListOf<String>()
        var inReasons = false
        for (line in lines) {
            if (line.trim().startsWith("Selection reasons:")) {
                inReasons = true
                continue
            }
            if (!inReasons) continue
            val match = reasonLine.matchEntire(line)
            if (match != null) {
                reasons.add(match.groupValues[1].trim())
                continue
            }
            if (line.isBlank()) continue
            if (line.startsWith(" ") && !line.trimStart().startsWith("-")) continue
            inReasons = false
        }
        return reasons
    }

    private fun extractPaths(lines: List<String>): List<String> {
        val paths = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!isGavHeader(line)) {
                index++
                continue
            }
            val block = mutableListOf(line)
            var cursor = index + 1
            var hasTree = false
            while (cursor < lines.size) {
                val next = lines[cursor]
                when {
                    isTreeLine(next) -> {
                        hasTree = true
                        block.add(next)
                        cursor++
                    }
                    next.isBlank() &&
                        cursor + 1 < lines.size &&
                        isTreeLine(lines[cursor + 1]) -> {
                        cursor++
                    }
                    else -> break
                }
            }
            if (hasTree) {
                paths.add(block.take(MAX_PATH_LINES).joinToString("\n"))
            }
            index = cursor
        }
        return paths
    }

    private fun isGavHeader(line: String): Boolean = gavHeader.matches(line.trim())

    private fun isTreeLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("\\---") ||
            trimmed.startsWith("+---") ||
            trimmed.startsWith("|")
    }
}
