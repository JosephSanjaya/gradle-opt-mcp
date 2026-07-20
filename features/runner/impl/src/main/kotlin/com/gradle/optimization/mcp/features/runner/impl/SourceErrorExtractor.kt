package com.gradle.optimization.mcp.features.runner.impl

import com.gradle.optimization.mcp.features.runner.api.GradleSourceError

object SourceErrorExtractor {
    private val kotlinErrorRegex = Regex(
        """^e:\s+(?:file://)?([^:\r\n]+):\s*\(?(\d+),\s*(\d+)\)?:\s*(.+)$""",
        RegexOption.MULTILINE
    )
    private val javaErrorRegex = Regex(
        """^([^\s:]+\.java):(\d+):\s*error:\s*(.+)$""",
        RegexOption.MULTILINE
    )
    private val gradleScriptRegex = Regex(
        """(?:Build file|Script)\s+'([^']+)'\s+line:\s*(\d+)""",
        RegexOption.MULTILINE
    )
    private val testFailureRegex = Regex(
        """^\s*([a-zA-Z0-9_.]+)\s+>\s+([a-zA-Z0-9_]+)\s+FAILED""",
        RegexOption.MULTILINE
    )

    @Suppress("MagicNumber")
    fun extractErrors(output: String): List<GradleSourceError> {
        val errors = mutableListOf<GradleSourceError>()

        kotlinErrorRegex.findAll(output).forEach { match ->
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull()
            val col = match.groupValues[3].toIntOrNull()
            val msg = match.groupValues[4].trim()
            errors.add(
                GradleSourceError(
                    file = file,
                    line = line,
                    column = col,
                    message = msg,
                    errorType = "KOTLIN_COMPILER_ERROR"
                )
            )
        }

        javaErrorRegex.findAll(output).forEach { match ->
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull()
            val msg = match.groupValues[3].trim()
            errors.add(
                GradleSourceError(
                    file = file,
                    line = line,
                    message = msg,
                    errorType = "JAVA_COMPILER_ERROR"
                )
            )
        }

        gradleScriptRegex.findAll(output).forEach { match ->
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull()
            errors.add(
                GradleSourceError(
                    file = file,
                    line = line,
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

        return errors.distinctBy { Triple(it.file, it.line, it.message) }
    }
}
