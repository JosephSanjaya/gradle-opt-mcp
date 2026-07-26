package com.gradle.optimization.mcp.features.cacheprofiler.impl

import com.gradle.optimization.mcp.features.cacheprofiler.api.CacheProfileRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CacheProfilerFeatureImplTest {
    @Test
    fun testNonExistentDirectoryThrows() {
        val impl = CacheProfilerFeatureImpl()
        assertFailsWith<IllegalArgumentException> {
            impl.profileCacheInvalidationTimeline(CacheProfileRequest(projectDir = "/non/existent/path"))
        }
    }

    @Test
    fun testHandlesEmptyCacheDirectoryGracefully() {
        val tempDir = Files.createTempDirectory("cache-profiler-test-empty").toFile()
        try {
            val impl = CacheProfilerFeatureImpl()
            val result = impl.profileCacheInvalidationTimeline(CacheProfileRequest(projectDir = tempDir.absolutePath))

            assertEquals(0, result.totalEntriesFound)
            assertTrue(result.entries.isEmpty())
            assertTrue(result.summary.contains("No configuration-cache-report.html"))
            assertEquals("audit_configuration_cache_inputs", result.preferAuditTool)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testIgnoresBinaryLockAndTmpNoise() {
        val tempDir = Files.createTempDirectory("cache-profiler-test-noise").toFile()
        try {
            val cacheDir = File(tempDir, ".gradle/configuration-cache")
            cacheDir.mkdirs()
            File(cacheDir, "entry-reused.bin").writeText("binary noise")
            File(cacheDir, "cache.lock").writeText("lock")
            val tmpDir = File(cacheDir, "build.tmp").apply { mkdirs() }
            File(tmpDir, "configuration-cache-report.html").writeText(reportHtml("storing", emptyList()))

            val impl = CacheProfilerFeatureImpl()
            val result = impl.profileCacheInvalidationTimeline(CacheProfileRequest(projectDir = tempDir.absolutePath))

            assertEquals(0, result.totalEntriesFound)
            assertTrue(result.entries.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProfilesConsecutiveReportsWithRealCacheActionAndInputDiffs() {
        val tempDir = Files.createTempDirectory("cache-profiler-test-reports").toFile()
        try {
            val reportRoot = File(tempDir, "build/reports/configuration-cache")
            val olderDir = File(reportRoot, "hash-old").apply { mkdirs() }
            val newerDir = File(reportRoot, "hash-new").apply { mkdirs() }

            val older = File(olderDir, "configuration-cache-report.html").apply {
                writeText(
                    reportHtml(
                        cacheAction = "storing",
                        inputs = listOf(
                            """{"input":[{"text":"environment variable "},{"name":"CI"}]}""",
                            """{"input":[{"text":"system property "},{"name":"os.name"}]}"""
                        ),
                        tasks = ":help"
                    )
                )
                setLastModified(1_000L)
            }
            val newer = File(newerDir, "configuration-cache-report.html").apply {
                writeText(
                    reportHtml(
                        cacheAction = "reused",
                        inputs = listOf(
                            """{"input":[{"text":"environment variable "},{"name":"CI"}]}""",
                            """{"input":[{"text":"file "},{"name":"gradle.properties"}]}"""
                        ),
                        tasks = ":help"
                    )
                )
                setLastModified(2_000L)
            }

            val impl = CacheProfilerFeatureImpl()
            val result = impl.profileCacheInvalidationTimeline(CacheProfileRequest(projectDir = tempDir.absolutePath))

            assertEquals(2, result.totalEntriesFound)
            assertEquals(2, result.entries.size)
            assertEquals("STORED", result.entries[0].status)
            assertEquals("storing", result.entries[0].cacheAction)
            assertEquals("REUSED", result.entries[1].status)
            assertEquals("reused", result.entries[1].cacheAction)
            assertEquals(listOf(":help"), result.entries[0].requestedTasks)
            assertTrue(result.entries[0].inputDiffSummary!!.contains("First report"))
            assertTrue(result.entries[1].addedInputs.any { it.contains("FILE:gradle.properties") })
            assertTrue(result.entries[1].removedInputs.any { it.contains("SYSTEM_PROPERTY:os.name") })
            assertEquals(older.absolutePath, result.entries[0].htmlReportPath)
            assertEquals(newer.absolutePath, result.entries[1].htmlReportPath)
            assertTrue(result.summary.contains("audit_configuration_cache_inputs"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun reportHtml(
        cacheAction: String,
        inputs: List<String>,
        tasks: String = ":help"
    ): String {
        val diagnostics = inputs.joinToString(",")
        return """
            <html><body>
            <script type="application/json">
            {"diagnostics":[$diagnostics],
            "totalProblemCount":0,
            "requestedTasks":"$tasks",
            "cacheAction":"$cacheAction",
            "cacheActionDescription":[{"text":"cache action for $tasks"}]}
            </script>
            </body></html>
        """.trimIndent()
    }
}
