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

        val maxFindings = request.maxFindings.coerceAtLeast(0)
        val targetFiles = findEligibleScriptFiles(rootDir)
        val allViolations = mutableListOf<LinterViolation>()

        for (file in targetFiles) {
            val relativePath = file.relativeTo(rootDir).path
            val lines = file.readLines()
            var inTaskActionBlock = false
            var pendingTaskAction = false
            var braceDepth = 0

            for ((index, line) in lines.withIndex()) {
                val lineNumber = index + 1
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) continue

                when {
                    TASK_ACTION_WITH_BRACE_REGEX.containsMatchIn(trimmed) -> {
                        inTaskActionBlock = true
                        pendingTaskAction = false
                        braceDepth = 0
                    }
                    TASK_ACTION_KEYWORD_REGEX.containsMatchIn(trimmed) -> {
                        pendingTaskAction = true
                    }
                    pendingTaskAction && trimmed.contains('{') -> {
                        inTaskActionBlock = true
                        pendingTaskAction = false
                        braceDepth = 0
                    }
                }

                if (inTaskActionBlock) {
                    braceDepth += trimmed.count { it == '{' } - trimmed.count { it == '}' }
                    if (PROJECT_MEMBER_ACCESS_REGEX.containsMatchIn(trimmed)) {
                        allViolations.add(
                            LinterViolation(
                                file = relativePath,
                                line = lineNumber,
                                ruleId = RULE_TASK_ACTION_PROJECT_ACCESS,
                                category = "Task Action Project Access",
                                message = "Accessing 'project.' inside task action breaks Gradle Configuration Cache.",
                                recommendation = "Inject properties via Gradle Property API (e.g. Property<String>).",
                                snippet = trimmed
                            )
                        )
                    }
                    if (braceDepth <= 0 && !TASK_ACTION_WITH_BRACE_REGEX.containsMatchIn(trimmed)) {
                        inTaskActionBlock = false
                    }
                }

                checkLineRules(relativePath, lineNumber, trimmed, allViolations)
            }
        }

        val totalViolations = allViolations.size
        val capped = if (maxFindings == 0) emptyList() else allViolations.take(maxFindings)
        val truncated = capped.size < totalViolations

        val summary = buildString {
            append("Gradle Plugin & Build Script Linter Report:\n")
            append("- Scope: static anti-pattern scan (not a full Configuration Cache audit)\n")
            append("- Scanned Files: ${targetFiles.size}\n")
            append("- Total Violations Found: $totalViolations\n")
            if (truncated) {
                append("- Showing: ${capped.size} (capped by maxFindings=$maxFindings)\n")
            }
            if (capped.isNotEmpty()) {
                val byCategory = capped.groupBy { it.category }
                byCategory.forEach { (category, list) ->
                    append("- $category: ${list.size}\n")
                }
            } else {
                append("- Rules checked: ${RULES_CHECKED.joinToString(", ")}\n")
                append("- Scanned roots: ${SCANNED_ROOTS.joinToString("; ")}\n")
                append(
                    "- Note: zero hits means no matches for these static rules in scanned roots; " +
                        "use audit_configuration_cache_inputs for runtime Configuration Cache inputs.\n"
                )
            }
        }

        return PluginLinterResult(
            projectDir = rootDir.absolutePath,
            scannedFilesCount = targetFiles.size,
            totalViolations = totalViolations,
            violations = capped,
            truncated = truncated,
            rulesChecked = RULES_CHECKED,
            scannedRoots = SCANNED_ROOTS,
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
                    ruleId = RULE_EAGER_TASK_CREATION,
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
                    ruleId = RULE_UNSAFE_COLLECTION_QUERY,
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
                    ruleId = RULE_PROVIDER_TO_STRING,
                    category = "Provider.toString() Bug",
                    message = "Calling toString() on Provider prints wrapper object rather than value.",
                    recommendation = "Use provider.get() or provider.orNull to extract property value.",
                    snippet = trimmed
                )
            )
        }
    }

    private companion object {
        const val RULE_EAGER_TASK_CREATION = "EAGER_TASK_CREATION"
        const val RULE_UNSAFE_COLLECTION_QUERY = "UNSAFE_COLLECTION_QUERY"
        const val RULE_TASK_ACTION_PROJECT_ACCESS = "TASK_ACTION_PROJECT_ACCESS"
        const val RULE_PROVIDER_TO_STRING = "PROVIDER_TO_STRING"

        val RULES_CHECKED = listOf(
            RULE_EAGER_TASK_CREATION,
            RULE_UNSAFE_COLLECTION_QUERY,
            RULE_TASK_ACTION_PROJECT_ACCESS,
            RULE_PROVIDER_TO_STRING
        )

        val SCANNED_ROOTS = listOf(
            "*.gradle / *.gradle.kts (project tree)",
            "buildSrc/**",
            "build-logic/**"
        )

        val EAGER_TASK_CREATION_REGEX = Regex("""\btasks\s*\.\s*(create|getByPath|getByName)\b""")
        val UNSAFE_COLLECTION_QUERY_REGEX = Regex("""\btasks\s*(\.\s*withType<[^>]+>\s*\(\s*\))?\s*\.\s*all\b""")
        val TASK_ACTION_WITH_BRACE_REGEX = Regex("""\b(doLast|doFirst)\s*\{|@TaskAction\b""")
        val TASK_ACTION_KEYWORD_REGEX = Regex("""\b(doLast|doFirst)\s*$""")
        // Member access only — avoids FP on params / identifiers / string "project".
        val PROJECT_MEMBER_ACCESS_REGEX = Regex("""\bproject\s*\.""")
        // \w* so bare provider.toString() matches, not only *Provider names.
        val PROVIDER_TO_STRING_REGEX =
            Regex("""\b\w*[Pp]rovider\s*\.\s*toString\s*\(\s*\)|\b\w*[Pp]roperty\s*\.\s*toString\s*\(\s*\)""")
    }
}
