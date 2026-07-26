package com.gradle.optimization.mcp.features.parallelism.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.parallelism.api.CriticalPathNode
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisRequest
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisResult
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismCount
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

private const val HEURISTIC_SAMPLE_LIMIT = 25
private const val TOP_BOTTLENECK_LIMIT = 5
private const val SHALLOW_TASK_THRESHOLD = 3
private const val FAILURE_REASON_MAX_CHARS = 1200
private const val FAILURE_REASON_MAX_LINES = 12

private val SHALLOW_LEAF_TYPES = setOf(
    "help",
    "tasks",
    "properties",
    "dependencies",
    "projects",
    "outgoingVariants",
    "resolvableConfigurations"
)

private val HEURISTIC_SEQUENTIAL_SUFFIXES = listOf(
    ":compileKotlin",
    ":compileJava",
    ":jar",
    ":classes",
    ":assemble",
    ":build",
    ":check",
    ":test"
)

@Single
class ParallelismFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ParallelismFeatureApi {
    override fun analyzeParallelizationBottlenecks(request: ParallelismAnalysisRequest): ParallelismAnalysisResult {
        require(request.projectDir.isNotBlank()) { "projectDir is required" }
        val targetDir = File(request.projectDir).canonicalFile
        require(targetDir.isDirectory) {
            "Project directory does not exist or is not a directory: ${request.projectDir}"
        }
        require(isGradleProjectRoot(targetDir)) {
            "Not a Gradle project root (missing settings.gradle(.kts) or build.gradle(.kts)): ${targetDir.path}"
        }

        val taskList = request.tasks.ifEmpty { listOf("build") }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var exceptionMessage: String? = null

        val toolingOk = runCatching {
            pool.withConnection(targetDir) { connection ->
                connection.newBuild()
                    .forTasks(*taskList.toTypedArray())
                    .withArguments("--dry-run", "--console=plain")
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .run()
            }
            true
        }.getOrElse { error ->
            exceptionMessage = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            false
        }

        val combinedOutput = buildString {
            append(stdout.toString(Charsets.UTF_8))
            append('\n')
            append(stderr.toString(Charsets.UTF_8))
            if (!exceptionMessage.isNullOrBlank() && !contains(exceptionMessage!!)) {
                append('\n')
                append(exceptionMessage)
            }
        }

        val taskPaths = ParallelismOutputParser.parseTaskPaths(combinedOutput)
        val failureReason = if (toolingOk) {
            null
        } else {
            ParallelismOutputParser.extractFailureReason(combinedOutput) ?: exceptionMessage
        }

        if (!toolingOk) {
            return ParallelismAnalysisResult(
                projectDir = targetDir.path,
                success = false,
                requestedTasks = taskList,
                analysisStatus = ParallelismAnalysisResult.STATUS_FAILED,
                taskCount = taskPaths.size,
                byModule = ParallelismOutputParser.countByModule(taskPaths),
                byLeafType = ParallelismOutputParser.countByLeafType(taskPaths),
                heuristicCriticalTasks = emptyList(),
                heuristicCriticalTasksTruncated = false,
                bottleneckModules = emptyList(),
                caveat = ParallelismAnalysisResult.CAVEAT,
                guidance = null,
                failureReason = failureReason,
                summary = buildString {
                    append("Parallelism analysis failed for ${taskList.joinToString(", ")} at ${targetDir.path}.")
                    if (!failureReason.isNullOrBlank()) {
                        append(" Reason: ${failureReason.lineSequence().first()}")
                    }
                }
            )
        }

        val byModule = ParallelismOutputParser.countByModule(taskPaths)
        val byLeafType = ParallelismOutputParser.countByLeafType(taskPaths)

        if (taskPaths.isEmpty()) {
            return ParallelismAnalysisResult(
                projectDir = targetDir.path,
                success = true,
                requestedTasks = taskList,
                analysisStatus = ParallelismAnalysisResult.STATUS_EMPTY_GRAPH,
                taskCount = 0,
                byModule = emptyList(),
                byLeafType = emptyList(),
                heuristicCriticalTasks = emptyList(),
                heuristicCriticalTasksTruncated = false,
                bottleneckModules = emptyList(),
                caveat = ParallelismAnalysisResult.CAVEAT,
                guidance = GUIDANCE_EMPTY,
                failureReason = null,
                summary = "Empty scheduled task graph for ${taskList.joinToString(", ")} at ${targetDir.path}. " +
                    "Not enough signal for parallelism heuristics."
            )
        }

        if (isShallowGraph(taskPaths, taskList)) {
            return ParallelismAnalysisResult(
                projectDir = targetDir.path,
                success = true,
                requestedTasks = taskList,
                analysisStatus = ParallelismAnalysisResult.STATUS_SHALLOW_GRAPH,
                taskCount = taskPaths.size,
                byModule = byModule,
                byLeafType = byLeafType,
                heuristicCriticalTasks = emptyList(),
                heuristicCriticalTasksTruncated = false,
                bottleneckModules = emptyList(),
                caveat = ParallelismAnalysisResult.CAVEAT,
                guidance = GUIDANCE_SHALLOW,
                failureReason = null,
                summary = "Shallow scheduled task graph (${taskPaths.size} task(s)) for " +
                    "${taskList.joinToString(", ")} at ${targetDir.path}. " +
                    "Info tasks like help are not useful for parallelism heuristics."
            )
        }

        val allHeuristic = selectHeuristicSequentialCandidates(taskPaths)
        val truncated = allHeuristic.size > HEURISTIC_SAMPLE_LIMIT
        val heuristicSample = allHeuristic.take(HEURISTIC_SAMPLE_LIMIT)
        val bottleneckModules = allHeuristic
            .groupingBy { it.projectPath }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_BOTTLENECK_LIMIT)
            .map { it.key }

        return ParallelismAnalysisResult(
            projectDir = targetDir.path,
            success = true,
            requestedTasks = taskList,
            analysisStatus = ParallelismAnalysisResult.STATUS_OK,
            taskCount = taskPaths.size,
            byModule = byModule,
            byLeafType = byLeafType,
            heuristicCriticalTasks = heuristicSample,
            heuristicCriticalTasksTruncated = truncated,
            bottleneckModules = bottleneckModules,
            caveat = ParallelismAnalysisResult.CAVEAT,
            guidance = null,
            failureReason = null,
            summary = buildString {
                append("Scheduled ${taskPaths.size} task(s) for ${taskList.joinToString(", ")} at ${targetDir.path}. ")
                append("Heuristic sequential candidates: ${allHeuristic.size}")
                if (truncated) append(" (showing $HEURISTIC_SAMPLE_LIMIT)")
                append(". Top modules by candidate count: ${bottleneckModules.joinToString(", ").ifBlank { "none" }}.")
            }
        )
    }

    private fun isGradleProjectRoot(projectDir: File): Boolean =
        File(projectDir, "settings.gradle.kts").isFile ||
            File(projectDir, "settings.gradle").isFile ||
            File(projectDir, "build.gradle.kts").isFile ||
            File(projectDir, "build.gradle").isFile

    private fun isShallowGraph(taskPaths: List<String>, requestedTasks: List<String>): Boolean {
        if (taskPaths.size < SHALLOW_TASK_THRESHOLD) return true
        val leaves = taskPaths.map { ParallelismOutputParser.leafType(it) }.toSet()
        if (leaves.isNotEmpty() && leaves.all { it in SHALLOW_LEAF_TYPES }) return true
        val requestedLeaves = requestedTasks.map { it.substringAfterLast(':') }
        return requestedLeaves.isNotEmpty() && requestedLeaves.all { it in SHALLOW_LEAF_TYPES }
    }

    private fun selectHeuristicSequentialCandidates(taskPaths: List<String>): List<CriticalPathNode> {
        val matched = taskPaths.filter { path ->
            HEURISTIC_SEQUENTIAL_SUFFIXES.any { path.endsWith(it) }
        }.ifEmpty { taskPaths }

        return matched.map { taskPath ->
            CriticalPathNode(
                taskPath = taskPath,
                projectPath = ParallelismOutputParser.modulePath(taskPath)
            )
        }
    }

    private companion object {
        const val GUIDANCE_EMPTY =
            "No scheduled tasks from dry-run. Call analyze_build_dry_run or re-run this tool with " +
                "recommended tasks: classes, assemble, or build (avoid help)."
        const val GUIDANCE_SHALLOW =
            "Task graph is too shallow for parallelism heuristics. Prefer tasks: classes, assemble, or build. " +
                "Also try analyze_build_dry_run for a fuller schedule summary."
    }
}

internal object ParallelismOutputParser {
    private val taskLineRegex = Regex("""^(:\S+)(?:\s+\S+)*\s*$""")

    fun parseTaskPaths(output: String): List<String> {
        val paths = mutableListOf<String>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith(":")) return@forEach
            val match = taskLineRegex.matchEntire(trimmed) ?: return@forEach
            paths.add(match.groupValues[1])
        }
        return paths
    }

    fun countByModule(taskPaths: List<String>): List<ParallelismCount> =
        taskPaths
            .groupingBy { modulePath(it) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ParallelismCount(name = it.key, count = it.value) }

    fun countByLeafType(taskPaths: List<String>): List<ParallelismCount> =
        taskPaths
            .groupingBy { leafType(it) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { ParallelismCount(name = it.key, count = it.value) }

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

    fun modulePath(taskPath: String): String {
        val lastColon = taskPath.lastIndexOf(':')
        return if (lastColon > 0) taskPath.substring(0, lastColon) else ":"
    }

    fun leafType(taskPath: String): String {
        val lastColon = taskPath.lastIndexOf(':')
        return if (lastColon >= 0 && lastColon < taskPath.lastIndex) {
            taskPath.substring(lastColon + 1)
        } else {
            taskPath
        }
    }
}
