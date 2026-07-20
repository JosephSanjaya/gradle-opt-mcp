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
            assertTrue(result.summary.contains("No Configuration Cache entries found"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProfilesEntriesAndFormatsTimeline() {
        val tempDir = Files.createTempDirectory("cache-profiler-test-entries").toFile()
        try {
            val cacheDir = File(tempDir, ".gradle/configuration-cache")
            cacheDir.mkdirs()
            val file1 = File(cacheDir, "cache-entry-1.bin")
            file1.writeText("dummy content 1")
            file1.setLastModified(System.currentTimeMillis() - 10000)

            val file2 = File(cacheDir, "cache-entry-2-reused.bin")
            file2.writeText("dummy content 2 long")
            file2.setLastModified(System.currentTimeMillis())

            val impl = CacheProfilerFeatureImpl()
            val result = impl.profileCacheInvalidationTimeline(CacheProfileRequest(projectDir = tempDir.absolutePath))

            assertEquals(2, result.totalEntriesFound)
            assertEquals(2, result.entries.size)
            assertTrue(result.entries.any { it.status == "REUSED" })
            assertTrue(result.entries.any { it.status == "STORED/INVALIDATED" })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
