package com.gradle.optimization.mcp.features.testsummary.impl

import java.io.File

internal data class StalenessStatus(
    val isStale: Boolean,
    val staleReason: String? = null
)

internal object StalenessChecker {
    fun checkStaleness(targetDir: File, xmlFiles: List<File>): StalenessStatus {
        if (xmlFiles.isEmpty()) {
            return StalenessStatus(isStale = false, staleReason = null)
        }

        val latestReportTime = xmlFiles.maxOf { it.lastModified() }
        var newestSourceFile: File? = null
        var newestSourceTime = 0L

        targetDir.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                name != "build" && !name.startsWith(".") && name != "out"
            }
            .filter { file ->
                file.isFile && isSourceFile(file)
            }
            .forEach { file ->
                val modTime = file.lastModified()
                if (modTime > newestSourceTime) {
                    newestSourceTime = modTime
                    newestSourceFile = file
                }
            }

        val currentNewest = newestSourceFile
        if (currentNewest != null && newestSourceTime > latestReportTime) {
            val relPath = currentNewest.relativeToOrNull(targetDir)?.path ?: currentNewest.name
            val diffSeconds = (newestSourceTime - latestReportTime) / THOUSAND
            val reason = "Source file '$relPath' was modified $diffSeconds s after test reports were generated."
            return StalenessStatus(isStale = true, staleReason = reason)
        }

        return StalenessStatus(isStale = false, staleReason = null)
    }

    private fun isSourceFile(file: File): Boolean {
        val name = file.name
        return name.endsWith(".kt") ||
            name.endsWith(".java") ||
            name.endsWith(".groovy") ||
            name.endsWith(".gradle.kts")
    }

    private const val THOUSAND = 1000L
}
