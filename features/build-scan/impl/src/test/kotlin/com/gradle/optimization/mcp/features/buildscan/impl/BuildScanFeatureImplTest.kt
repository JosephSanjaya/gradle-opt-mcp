package com.gradle.optimization.mcp.features.buildscan.impl

import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildScanFeatureImplTest {
    private val feature = BuildScanFeatureImpl()

    @Test
    fun projectDirOnlyReturnsNoDataWithoutRunningGradle() {
        val tempDir = tempProjectDir()
        val result = feature.analyzeBuildScan(
            BuildScanRequest(projectDir = tempDir.absolutePath)
        )
        assertEquals(BuildScanResult.STATUS_NO_DATA, result.status)
        assertEquals(0, result.totalTasksCount)
        assertNull(result.cacheHitRatio)
        assertTrue(result.recommendations.isEmpty())
        assertNotNull(result.guidance)
        assertNotNull(result.dumpFormatHint)
    }

    @Test
    fun buildScanUrlReturnsClearError() {
        val tempDir = tempProjectDir()
        val result = feature.analyzeBuildScan(
            BuildScanRequest(
                projectDir = tempDir.absolutePath,
                buildScanUrl = "https://scans.example/s/abc"
            )
        )
        assertEquals(BuildScanResult.STATUS_ERROR, result.status)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("not supported"))
        assertTrue(result.failureReason!!.contains("https://scans.example/s/abc"))
    }

    @Test
    fun missingDumpFileFailsClosed() {
        val tempDir = tempProjectDir()
        val missing = File(tempDir, "missing-scan.txt")
        val result = feature.analyzeBuildScan(
            BuildScanRequest(
                projectDir = tempDir.absolutePath,
                dumpFilePath = missing.absolutePath
            )
        )
        assertEquals(BuildScanResult.STATUS_ERROR, result.status)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("does not exist"))
    }

    @Test
    fun emptyDumpReturnsNoDataWithoutInventedMetrics() {
        val tempDir = tempProjectDir()
        val dump = File(tempDir, "empty.txt").apply {
            writeText("no task lines here\n")
            deleteOnExit()
        }
        val result = feature.analyzeBuildScan(
            BuildScanRequest(
                projectDir = tempDir.absolutePath,
                dumpFilePath = dump.absolutePath
            )
        )
        assertEquals(BuildScanResult.STATUS_NO_DATA, result.status)
        assertEquals(0, result.totalTasksCount)
        assertNull(result.cacheHitRatio)
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun parsesToyDumpWithRealDurationsAndCapsLists() {
        val tempDir = tempProjectDir()
        val dump = File(tempDir, "scan.txt").apply {
            writeText(
                """
                :compileKotlin FROM-CACHE 40ms
                :test UP-TO-DATE 10ms
                :assemble 200ms
                :lint EXECUTED 500ms
                :check EXECUTED
                GC pause: 120ms
                """.trimIndent()
            )
            deleteOnExit()
        }

        val result = feature.analyzeBuildScan(
            BuildScanRequest(
                projectDir = tempDir.absolutePath,
                dumpFilePath = dump.absolutePath,
                maxListedTasks = 1
            )
        )

        assertEquals(BuildScanResult.STATUS_OK, result.status)
        assertEquals(120L, result.gcPauseMs)
        assertEquals(5, result.totalTasksCount)
        assertEquals(2, result.cacheHitCount)
        assertEquals(3, result.cacheMissCount)
        assertNotNull(result.cacheHitRatio)
        assertEquals(1, result.longRunningTasks.size)
        assertEquals(":lint", result.longRunningTasks.first().taskPath)
        assertEquals(500L, result.longRunningTasks.first().durationMs)
        assertEquals(1, result.tasksWithCacheMisses.size)
        assertTrue(result.truncated)
        assertFalse(result.recommendations.isEmpty())
        // No invented duration for :check — excluded from longest-running
        assertTrue(result.longRunningTasks.none { it.taskPath == ":check" })
    }

    private fun tempProjectDir(): File =
        File.createTempFile("test-proj", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
}
