package com.gradle.optimization.mcp.features.cacheprofiler.impl

import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheInvalidationEntry
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileRequest
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileResult
import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfilerFeatureApi
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

        val cacheDir = File(targetDir, ".gradle/configuration-cache")
        val reportDir = File(targetDir, "build/reports/configuration-cache")

        if (!cacheDir.exists() && !reportDir.exists()) {
            return CacheProfileResult(
                projectDir = request.projectDir,
                totalEntriesFound = 0,
                entries = emptyList(),
                summary = "No Configuration Cache entries found in ${request.projectDir}."
            )
        }

        val entries = mutableListOf<CacheInvalidationEntry>()
        val foundFiles = mutableListOf<File>()

        if (cacheDir.exists() && cacheDir.isDirectory) {
            val children = cacheDir.listFiles() ?: emptyArray()
            foundFiles.addAll(children)
        }

        if (reportDir.exists() && reportDir.isDirectory) {
            val htmlReports = reportDir.walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".html") || it.name.endsWith(".json")) }
                .toList()
            foundFiles.addAll(htmlReports)
        }

        val sortedFiles = foundFiles
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .take(request.limit.coerceAtLeast(1))

        var previousFile: File? = null
        for (file in sortedFiles.reversed()) {
            val lastMod = file.lastModified()
            val formattedTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(lastMod).atZone(ZoneId.systemDefault()))

            val status = if (file.name.contains("reused", ignoreCase = true)) {
                "REUSED"
            } else {
                "STORED/INVALIDATED"
            }

            val diffSummary = if (previousFile != null) {
                val sizeDiff = file.length() - previousFile.length()
                "Size diff from previous entry: ${if (sizeDiff >= 0) "+$sizeDiff" else "$sizeDiff"} bytes"
            } else {
                "Initial cache entry in timeline"
            }

            val reasons = mutableListOf<String>()
            if (status == "STORED/INVALIDATED") {
                reasons.add("Configuration input hash changed or first initialization")
            }

            entries.add(
                CacheInvalidationEntry(
                    entryId = file.name,
                    timestamp = lastMod,
                    formattedTime = formattedTime,
                    status = status,
                    invalidationReasons = reasons,
                    inputDiffSummary = diffSummary
                )
            )
            previousFile = file
        }

        val chronologicalEntries = entries.reversed()
        val totalCount = foundFiles.size

        val summary = if (totalCount == 0) {
            "No active configuration cache entries located."
        } else {
            "Analyzed $totalCount cache entries; returning top ${chronologicalEntries.size} entries."
        }

        return CacheProfileResult(
            projectDir = request.projectDir,
            totalEntriesFound = totalCount,
            entries = chronologicalEntries,
            summary = summary
        )
    }
}
