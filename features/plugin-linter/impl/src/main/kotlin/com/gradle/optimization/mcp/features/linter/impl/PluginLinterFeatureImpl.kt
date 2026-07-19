package com.gradle.optimization.mcp.features.linter.impl

import com.gradle.optimization.mcp.features.linter.api.LinterViolation
import com.gradle.optimization.mcp.features.linter.api.PluginLinterFeatureApi
import com.gradle.optimization.mcp.features.linter.api.PluginLinterRequest
import com.gradle.optimization.mcp.features.linter.api.PluginLinterResult
import java.io.File
import org.koin.core.annotation.Single

@Single
class PluginLinterFeatureImpl : PluginLinterFeatureApi {
    override fun lintPlugins(request: PluginLinterRequest): PluginLinterResult {
        val rootDir = File(request.projectDir)
        require(rootDir.exists() && rootDir.isDirectory) {
            "Project directory does not exist or is not a directory: ${request.projectDir}"
        }

        val targetFiles = findEligibleScriptFiles(rootDir)
        val violations = mutableListOf<LinterViolation>()

        for (file in targetFiles) {
            val relativePath = file.relativeTo(rootDir).path
            val lines = file.readLines()
            var inTaskActionBlock = false
            var braceDepth = 0

            for ((index, line) in lines.withIndex()) {
                val lineNumber = index + 1
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue

                if (TASK_ACTION_START_REGEX.containsMatchIn(trimmed)) {
                    inTaskActionBlock = true
                    braceDepth = 0
                }

                if (inTaskActionBlock) {
                    braceDepth += trimmed.count { it == '{' } - trimmed.count { it == '}' }
                    if (PROJECT_ACCESS_REGEX.containsMatchIn(trimmed)) {
                        violations.add(
                            LinterViolation(
                                file = relativePath,
                                line = lineNumber,
                                ruleId = "TASK_ACTION_PROJECT_ACCESS",
                                category = "Task Action Project Access",
                                message = "Accessing 'project' inside task action breaks Gradle Configuration Cache.",
                                recommendation = "Inject properties via Gradle Property API (e.g. Property<String>).",
                                snippet = trimmed
                            )
                        )
                    }
                    if (braceDepth <= 0 && !TASK_ACTION_START_REGEX.containsMatchIn(trimmed)) {
                        inTaskActionBlock = false
                    }
                }

                checkLineRules(relativePath, lineNumber, trimmed, violations)
            }
        }

        val summary = buildString {
            append("Gradle Plugin & Build Script Linter Report:\n")
            append("- Scanned Files: ${targetFiles.size}\n")
            append("- Total Violations Found: ${violations.size}\n")
            if (violations.isNotEmpty()) {
                val byCategory = violations.groupBy { it.category }
                byCategory.forEach { (category, list) ->
                    append("- $category: ${list.size}\n")
                }
            }
        }

        return PluginLinterResult(
            projectDir = rootDir.absolutePath,
            scannedFilesCount = targetFiles.size,
            violations = violations,
            summary = summary
        )
    }

    private fun findEligibleScriptFiles(rootDir: File): List<File> {
        val eligible = mutableListOf<File>()
        val excludedDirs = setOf("build", ".gradle", ".git", ".idea", ".agents")

        rootDir.walkTopDown()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { file ->
                file.isFile && (
                    file.name.endsWith(".gradle.kts") ||
                        file.name.endsWith(".gradle") ||
                        (file.name.endsWith(".kt") && isInPluginSourceDir(file, rootDir)) ||
                        (file.name.endsWith(".groovy") && isInPluginSourceDir(file, rootDir))
                    )
            }
            .forEach { eligible.add(it) }

        return eligible
    }

    private fun isInPluginSourceDir(file: File, rootDir: File): Boolean {
        val relativePath = file.relativeTo(rootDir).path
        return relativePath.startsWith("buildSrc") || relativePath.startsWith("build-logic")
    }

    private fun checkLineRules(
        file: String,
        lineNum: Int,
        trimmed: String,
        violations: MutableList<LinterViolation>
    ) {
        if (EAGER_TASK_CREATION_REGEX.containsMatchIn(trimmed)) {
            violations.add(
                LinterViolation(
                    file = file,
                    line = lineNum,
                    ruleId = "EAGER_TASK_CREATION",
                    category = "Eager Task Configuration",
                    message = "Eager task creation forces immediate configuration during build startup.",
                    recommendation = "Use tasks.register() or tasks.named() for deferred task configuration.",
                    snippet = trimmed
                )
            )
        }

        if (UNSAFE_COLLECTION_QUERY_REGEX.containsMatchIn(trimmed)) {
            violations.add(
                LinterViolation(
                    file = file,
                    line = lineNum,
                    ruleId = "UNSAFE_COLLECTION_QUERY",
                    category = "Unsafe Collection Query",
                    message = "Unsafe task collection query forces eager instantiation of all matching tasks.",
                    recommendation = "Use tasks.withType<...>().configureEach { } instead.",
                    snippet = trimmed
                )
            )
        }

        if (PROVIDER_TO_STRING_REGEX.containsMatchIn(trimmed)) {
            violations.add(
                LinterViolation(
                    file = file,
                    line = lineNum,
                    ruleId = "PROVIDER_TO_STRING",
                    category = "Provider.toString() Bug",
                    message = "Calling toString() on Provider prints wrapper object rather than value.",
                    recommendation = "Use provider.get() or provider.orNull to extract property value.",
                    snippet = trimmed
                )
            )
        }
    }

    private companion object {
        val EAGER_TASK_CREATION_REGEX = Regex("""\btasks\s*\.\s*(create|getByPath|getByName)\b""")
        val UNSAFE_COLLECTION_QUERY_REGEX = Regex("""\btasks\s*(\.\s*withType<[^>]+>\s*\(\s*\))?\s*\.\s*all\b""")
        val TASK_ACTION_START_REGEX = Regex("""\b(doLast|doFirst)\s*\{|@TaskAction\b""")
        val PROJECT_ACCESS_REGEX = Regex("""\bproject\b""")
        val PROVIDER_TO_STRING_REGEX = Regex("""\b\w+[Pp]rovider\s*\.\s*toString\s*\(\s*\)""")
    }
}
