package com.gradle.optimization.mcp.features.cacheprofiler.impl

import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheInvalidationEntry
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileRequest
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileResult
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfilerFeatureApi
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheInputViolation
import com.gradle.optimization.mcp.features.configcache.impl.ConfigurationCacheReportParser
import com.gradle.optimization.mcp.features.configcache.impl.ParsedConfigurationCacheReport
import org.koin.core.annotation.Single
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Single
class CacheProfilerFeatureImpl : CacheProfilerFeatureApi {
    override fun profileCacheInvalidationTimeline(request: CacheProfileRequest): CacheProfileResult {
        val targetDir = File(request.projectDir)
        require(targetDir.exists()) { "Target directory does not exist: ${request.projectDir}" }

        val reportFiles = findConfigurationCacheReports(targetDir)
        if (reportFiles.isEmpty()) {
            return CacheProfileResult(
                projectDir = request.projectDir,
                totalEntriesFound = 0,
                entries = emptyList(),
                summary = "No configuration-cache-report.html files found under " +
                    "build/reports/configuration-cache or .gradle/configuration-cache. " +
                    "Prefer audit_configuration_cache_inputs to generate and audit a single-build report."
            )
        }

        val timed = reportFiles.mapNotNull { file ->
            val parsed = ConfigurationCacheReportParser.parseReportHtml(file.readText())
                ?: return@mapNotNull null
            TimedReport(file = file, parsed = parsed)
        }.sortedBy { it.file.lastModified() }

        if (timed.isEmpty()) {
            return CacheProfileResult(
                projectDir = request.projectDir,
                totalEntriesFound = reportFiles.size,
                entries = emptyList(),
                summary = "Found ${reportFiles.size} configuration-cache report file(s) but none contained " +
                    "parsable embedded JSON. Prefer audit_configuration_cache_inputs for a fresh report."
            )
        }

        val limited = timed.takeLast(request.limit.coerceAtLeast(1))
        val entries = buildEntries(limited)

        val storedCount = entries.count { it.status == STATUS_STORED }
        val reusedCount = entries.count { it.status == STATUS_REUSED }
        val summary = buildString {
            append("Parsed ${entries.size} of ${timed.size} configuration-cache report(s) ")
            append("(STORED=$storedCount, REUSED=$reusedCount). ")
            append("Input diffs compare consecutive reports' diagnostic inputs. ")
            append("For a deep single-build input audit, prefer audit_configuration_cache_inputs.")
        }

        return CacheProfileResult(
            projectDir = request.projectDir,
            totalEntriesFound = timed.size,
            entries = entries,
            summary = summary
        )
    }

    private fun buildEntries(reports: List<TimedReport>): List<CacheInvalidationEntry> {
        val entries = ArrayList<CacheInvalidationEntry>(reports.size)
        var previousKeys: Set<String>? = null

        for (report in reports) {
            val lastMod = report.file.lastModified()
            val formattedTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(lastMod).atZone(ZoneId.systemDefault()))
            val status = statusFromCacheAction(report.parsed.cacheAction)
            val currentKeys = report.parsed.inputs.map(::inputKey).toSet()

            val added = if (previousKeys == null) {
                emptyList()
            } else {
                (currentKeys - previousKeys).sorted().take(MAX_INPUT_DIFF)
            }
            val removed = if (previousKeys == null) {
                emptyList()
            } else {
                (previousKeys - currentKeys).sorted().take(MAX_INPUT_DIFF)
            }

            val reasons = buildList {
                report.parsed.cacheActionDescription?.takeIf { it.isNotBlank() }?.let(::add)
                if (status == STATUS_STORED && previousKeys != null) {
                    add("Configuration cache was stored (not reused) relative to previous report")
                }
            }

            val diffSummary = when {
                previousKeys == null -> "First report in timeline"
                added.isEmpty() && removed.isEmpty() -> "No input set changes vs previous report"
                else -> buildString {
                    if (added.isNotEmpty()) {
                        append("Added (${added.size}): ${added.joinToString()}")
                    }
                    if (removed.isNotEmpty()) {
                        if (isNotEmpty()) append("; ")
                        append("Removed (${removed.size}): ${removed.joinToString()}")
                    }
                }
            }

            entries.add(
                CacheInvalidationEntry(
                    entryId = reportEntryId(report.file),
                    timestamp = lastMod,
                    formattedTime = formattedTime,
                    status = status,
                    cacheAction = report.parsed.cacheAction,
                    requestedTasks = report.parsed.requestedTasks,
                    invalidationReasons = reasons,
                    addedInputs = added,
                    removedInputs = removed,
                    inputDiffSummary = diffSummary,
                    htmlReportPath = report.file.absolutePath
                )
            )
            previousKeys = currentKeys
        }
        return entries
    }

    private fun findConfigurationCacheReports(projectDir: File): List<File> {
        val roots = listOf(
            File(projectDir, "build/reports/configuration-cache"),
            File(projectDir, ".gradle/configuration-cache")
        )
        return roots
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { dir -> !isNoiseDir(dir) }
                    .filter { it.isFile && it.name == REPORT_FILE_NAME }
                    .filter { !isNoisePath(it) }
                    .toList()
            }
            .distinctBy { it.absolutePath }
    }

    private fun isNoiseDir(dir: File): Boolean {
        val name = dir.name
        return name.endsWith(".lock") ||
            name.endsWith(".tmp") ||
            name.endsWith(".part") ||
            name == "tmp"
    }

    private fun isNoisePath(file: File): Boolean {
        val path = file.path
        return path.contains("${File.separator}.tmp${File.separator}") ||
            path.contains("${File.separator}tmp${File.separator}") ||
            file.name.endsWith(".lock") ||
            file.name.endsWith(".tmp")
    }

    private fun reportEntryId(file: File): String {
        val parent = file.parentFile?.name?.takeIf { it.isNotBlank() && it != "configuration-cache" }
        return parent ?: file.name
    }

    private fun statusFromCacheAction(action: String?): String = when {
        action.equals("reused", ignoreCase = true) -> STATUS_REUSED
        action.equals("storing", ignoreCase = true) ||
            action.equals("stored", ignoreCase = true) -> STATUS_STORED
        action.isNullOrBlank() -> STATUS_UNKNOWN
        else -> action.uppercase()
    }

    private fun inputKey(input: ConfigCacheInputViolation): String =
        "${input.inputType}:${input.inputName}"

    private data class TimedReport(
        val file: File,
        val parsed: ParsedConfigurationCacheReport
    )

    private companion object {
        const val REPORT_FILE_NAME = "configuration-cache-report.html"
        const val MAX_INPUT_DIFF = 20
        const val STATUS_STORED = "STORED"
        const val STATUS_REUSED = "REUSED"
        const val STATUS_UNKNOWN = "UNKNOWN"
    }
}
