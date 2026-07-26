package com.gradle.optimization.mcp.features.configcache.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditResult
import com.gradle.optimization.mcp.features.configcache.api.ConfigurationCacheFeatureApi
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File

@Single
class ConfigurationCacheFeatureImpl(
    @Provided private val pool: GradleConnectionPool
) : ConfigurationCacheFeatureApi {
    override fun auditConfigurationCacheInputs(request: ConfigCacheAuditRequest): ConfigCacheAuditResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Target directory does not exist: ${request.projectDir}" }

        val taskList = request.tasks.ifEmpty { listOf("help") }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val startedAtMs = System.currentTimeMillis()
        var failureReason: String? = null

        val success = runCatching {
            pool.withConnection(targetDir) { connection ->
                val launcher = connection.newBuild()
                    .forTasks(*taskList.toTypedArray())
                    .withArguments(
                        "-Dorg.gradle.configuration-cache=true",
                        "--console=plain"
                    )
                    .setStandardOutput(stdout)
                    .setStandardError(stderr)
                launcher.run()
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
            if (!failureReason.isNullOrBlank() && !contains(failureReason!!)) {
                append('\n')
                append(failureReason)
            }
        }

        val consoleCacheHit = rawOutput.contains("Configuration cache entry reused", ignoreCase = true) ||
            rawOutput.contains("Reusing configuration cache", ignoreCase = true)

        val reportFromOutput = ConfigurationCacheReportParser.extractReportPathFromOutput(rawOutput)
            ?.let { File(it) }
            ?.takeIf { it.exists() }
        val reportFile = reportFromOutput
            ?: ConfigurationCacheReportParser.findNewestReport(targetDir, startedAtMs)

        val parsed = reportFile
            ?.takeIf { it.exists() }
            ?.let { ConfigurationCacheReportParser.parseReportHtml(it.readText()) }

        val cacheAction = parsed?.cacheAction
            ?: when {
                consoleCacheHit -> "reused"
                rawOutput.contains("Configuration cache entry stored", ignoreCase = true) -> "stored"
                else -> null
            }
        val cacheHit = consoleCacheHit || cacheAction.equals("reused", ignoreCase = true)

        val notableInputs = ConfigurationCacheReportParser.selectNotableInputs(
            inputs = parsed?.inputs.orEmpty(),
            maxNotable = request.maxNotableInputs
        )
        val problems = parsed?.problems.orEmpty()
        val totalProblemCount = parsed?.totalProblemCount ?: 0
        val requestedFromReport = parsed?.requestedTasks.orEmpty()
        val effectiveRequested = requestedFromReport.ifEmpty { taskList }

        val summary = buildSummary(
            success = success,
            cacheHit = cacheHit,
            cacheAction = cacheAction,
            totalInputs = parsed?.inputs?.size ?: 0,
            notableCount = notableInputs.size,
            totalProblemCount = totalProblemCount,
            failureReason = failureReason
        )

        return ConfigCacheAuditResult(
            projectDir = request.projectDir,
            success = success && totalProblemCount == 0,
            cacheHit = cacheHit,
            cacheAction = cacheAction,
            requestedTasks = effectiveRequested,
            totalInputs = parsed?.inputs?.size ?: 0,
            inputCounts = parsed?.inputCounts.orEmpty(),
            notableInputs = notableInputs,
            totalProblemCount = totalProblemCount,
            problems = problems,
            htmlReportPath = reportFile?.absolutePath,
            failureReason = failureReason,
            summary = summary
        )
    }

    private fun buildSummary(
        success: Boolean,
        cacheHit: Boolean,
        cacheAction: String?,
        totalInputs: Int,
        notableCount: Int,
        totalProblemCount: Int,
        failureReason: String?
    ): String = buildString {
        when {
            !success -> {
                append("Configuration cache audit build failed.")
                if (!failureReason.isNullOrBlank()) {
                    append(" Reason: ${failureReason.lineSequence().first()}")
                }
            }
            totalProblemCount > 0 -> {
                append("Configuration cache reported $totalProblemCount problem(s).")
            }
            cacheHit -> append("Configuration cache hit (entry reused).")
            cacheAction != null -> append("Configuration cache action: $cacheAction.")
            else -> append("Configuration cache audit completed.")
        }
        if (totalInputs > 0) {
            append(" Tracked $totalInputs configuration input(s)")
            if (notableCount > 0) {
                append(", $notableCount notable")
            }
            append('.')
        }
    }.trim()
}
