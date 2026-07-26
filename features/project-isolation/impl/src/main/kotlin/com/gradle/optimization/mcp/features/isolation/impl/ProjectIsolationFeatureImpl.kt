package com.gradle.optimization.mcp.features.isolation.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.impl.ConfigurationCacheReportParser
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckRequest
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckResult
import com.gradle.optimization.mcp.features.isolation.api.ProjectIsolationFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class ProjectIsolationFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ProjectIsolationFeatureApi {
    override fun checkProjectIsolation(request: IsolationCheckRequest): IsolationCheckResult {
        val targetDir = File(request.projectDir).canonicalFile
        require(targetDir.exists() && targetDir.isDirectory) {
            "Project directory does not exist: ${request.projectDir}"
        }
        require(isGradleProjectRoot(targetDir)) {
            "Not a Gradle project root (missing settings.gradle(.kts) or build.gradle(.kts)): ${targetDir.path}"
        }

        if (request.recreateCache) {
            deleteConfigurationCacheState(targetDir)
        }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val startedAtMs = System.currentTimeMillis()
        var failureReason: String? = null

        val toolingOk = runCatching {
            pool.withConnection(targetDir) { connection ->
                connection.newBuild()
                    .forTasks("help")
                    .withArguments(
                        "-Dorg.gradle.unsafe.isolated-projects=true",
                        "--console=plain"
                    )
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                    .run()
            }
            true
        }.getOrElse { error ->
            failureReason = error.message?.takeIf { it.isNotBlank() } ?: error.toString()
            false
        }

        val rawOutput = buildString {
            append(stdout.toString(Charsets.UTF_8))
            append('\n')
            append(stderr.toString(Charsets.UTF_8))
            val reason = failureReason
            if (!reason.isNullOrBlank() && !contains(reason)) {
                append('\n')
                append(reason)
            }
        }

        val reportFile = resolveReportFile(targetDir, rawOutput, startedAtMs)
        val parsed = reportFile
            ?.takeIf { it.exists() }
            ?.let { ConfigurationCacheReportParser.parseReportHtml(it.readText()) }

        val fromReport = ProjectIsolationViolationMapper.fromReportProblems(parsed?.problems.orEmpty())
        val violations = fromReport.ifEmpty {
            ProjectIsolationViolationMapper.fromConsoleOutput(rawOutput)
        }

        val maxViolations = request.maxViolations.coerceAtLeast(0)
        val capped = if (maxViolations == 0) emptyList() else violations.take(maxViolations)
        val totalCount = violations.size

        // Isolation problems often fail the Gradle invocation; still a successful check if we parsed them.
        val checkSucceeded = toolingOk || totalCount > 0
        val isIsolated = toolingOk && totalCount == 0
        val effectiveFailure = if (checkSucceeded) {
            null
        } else {
            failureReason ?: "Gradle Tooling API failed while checking project isolation"
        }

        val summary = buildSummary(
            projectDir = targetDir.path,
            isIsolated = isIsolated,
            totalCount = totalCount,
            shownCount = capped.size,
            failureReason = effectiveFailure
        )

        return IsolationCheckResult(
            projectDir = targetDir.path,
            success = checkSucceeded,
            isIsolated = isIsolated,
            totalViolationCount = totalCount,
            violations = capped,
            htmlReportPath = reportFile?.absolutePath,
            failureReason = effectiveFailure,
            summary = summary
        )
    }

    private fun buildSummary(
        projectDir: String,
        isIsolated: Boolean,
        totalCount: Int,
        shownCount: Int,
        failureReason: String?
    ): String = buildString {
        when {
            !failureReason.isNullOrBlank() -> {
                append("Project isolation check failed for $projectDir.")
                append(" Reason: ${failureReason.lineSequence().first()}")
            }
            isIsolated -> append("No project isolation violations detected in $projectDir.")
            else -> {
                append("Found $totalCount project isolation violation(s) in $projectDir.")
                if (shownCount < totalCount) {
                    append(" Showing $shownCount.")
                }
            }
        }
    }.trim()

    private fun resolveReportFile(projectDir: File, output: String, startedAtMs: Long): File? {
        val fromOutput = ConfigurationCacheReportParser.extractReportPathFromOutput(output)
            ?.let { File(it) }
            ?.takeIf { it.exists() }
        if (fromOutput != null) return fromOutput

        return findNewestIsolationReport(projectDir, startedAtMs)
            ?: ConfigurationCacheReportParser.findNewestReport(projectDir, startedAtMs)
    }

    private fun findNewestIsolationReport(projectDir: File, createdAfterMs: Long): File? {
        val skewMs = REPORT_MTIME_SKEW_MS
        val candidates = listOf(
            File(projectDir, "build/reports/configuration-cache"),
            File(projectDir, ".gradle/configuration-cache")
        ).filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.name == REPORT_FILE_NAME }
                    .toList()
            }
        if (candidates.isEmpty()) return null
        return candidates
            .filter { it.lastModified() >= createdAfterMs - skewMs }
            .maxByOrNull { it.lastModified() }
            ?: candidates.maxByOrNull { it.lastModified() }
    }

    private fun deleteConfigurationCacheState(projectDir: File) {
        listOf(
            File(projectDir, "build/reports/configuration-cache"),
            File(projectDir, ".gradle/configuration-cache")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun isGradleProjectRoot(projectDir: File): Boolean =
        File(projectDir, "settings.gradle.kts").isFile ||
            File(projectDir, "settings.gradle").isFile ||
            File(projectDir, "build.gradle.kts").isFile ||
            File(projectDir, "build.gradle").isFile

    private companion object {
        const val REPORT_FILE_NAME = "configuration-cache-report.html"
        const val REPORT_MTIME_SKEW_MS = 2_000L
    }
}
