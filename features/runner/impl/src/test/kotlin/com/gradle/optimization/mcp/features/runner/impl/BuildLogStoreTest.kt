package com.gradle.optimization.mcp.features.runner.impl

import com.gradle.optimization.mcp.features.runner.api.GradleRunLogRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildLogStoreTest {
    @Test
    fun testCleanAndDeduplicate() {
        val raw = "Line 1\nLine 2\nLine 2\nLine 2\nLine 3"
        val cleaned = BuildLogStore.cleanAndDeduplicate(raw)

        assertEquals(3, cleaned.size)
        assertEquals("Line 1", cleaned[0])
        assertEquals("Line 2 [x3]", cleaned[1])
        assertEquals("Line 3", cleaned[2])
    }

    @Test
    fun testSaveAndReadLog() {
        val tempDir = File.createTempFile("test_build_log_store", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        val runId = BuildLogStore.generateRunId()
        val lines = listOf("Task :compile", "Task :test", "BUILD SUCCESSFUL")

        BuildLogStore.saveLog(tempDir, runId, lines)

        val result = BuildLogStore.readLog(
            tempDir,
            GradleRunLogRequest(
                projectDir = tempDir.absolutePath,
                runId = runId,
                offset = 0,
                limit = 2
            )
        )

        assertEquals(runId, result.runId)
        assertEquals(3, result.totalLines)
        assertEquals(2, result.lines.size)
        assertTrue(result.hasMore)
        assertEquals("Task :compile", result.lines[0])
        assertEquals("Task :test", result.lines[1])
    }

    @Test
    fun testReadLogWithFilter() {
        val tempDir = File.createTempFile("test_build_log_filter", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        val runId = BuildLogStore.generateRunId()
        val lines = listOf("Task :compile", "e: Error in File.kt", "BUILD FAILED")

        BuildLogStore.saveLog(tempDir, runId, lines)

        val result = BuildLogStore.readLog(
            tempDir,
            GradleRunLogRequest(
                projectDir = tempDir.absolutePath,
                runId = runId,
                filter = "Error"
            )
        )

        assertEquals(1, result.totalLines)
        assertEquals("e: Error in File.kt", result.lines[0])
        assertFalse(result.hasMore)
    }
}
