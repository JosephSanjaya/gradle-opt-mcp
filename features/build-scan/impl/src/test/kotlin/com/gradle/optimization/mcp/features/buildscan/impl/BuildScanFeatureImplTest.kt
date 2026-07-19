package com.gradle.optimization.mcp.features.buildscan.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.buildscan.api.BuildScanRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildScanFeatureImplTest {
    @Test
    fun testAnalyzeBuildScanWithLocalFile() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for unit test")
            }
        }
        val feature = BuildScanFeatureImpl(fakePool)
        val tempDir = File.createTempFile("test-proj", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        val tempLog = File(tempDir, "scan.txt").apply {
            writeText(":compileKotlin FROM-CACHE\n:test UP-TO-DATE\n:assemble\nGC pause: 120ms\n")
            deleteOnExit()
        }

        val result = feature.analyzeBuildScan(
            BuildScanRequest(
                projectDir = tempDir.absolutePath,
                dumpFilePath = tempLog.absolutePath
            )
        )

        assertNotNull(result)
        assertEquals(120L, result.gcPauseMs)
        assertEquals(3, result.totalTasksCount)
        assertEquals(1, result.tasksWithCacheMisses.size)
        assertTrue(result.recommendations.isNotEmpty())
    }
}
