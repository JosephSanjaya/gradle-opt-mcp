package com.gradle.optimization.mcp.features.testsummary.impl

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StalenessCheckerTest {
    @Test
    fun testNotStaleWhenXmlIsNewer() {
        val tempDir = createTempDirectory("staletest").toFile()
        try {
            val srcDir = File(tempDir, "src/main/kotlin").apply { mkdirs() }
            File(srcDir, "Foo.kt").apply {
                writeText("class Foo")
                setLastModified(1000L)
            }

            val xmlDir = File(tempDir, "build/test-results/test").apply { mkdirs() }
            val xmlFile = File(xmlDir, "TEST-FooTest.xml").apply {
                writeText("<testsuite name=\"FooTest\" tests=\"1\"/>")
                setLastModified(2000L)
            }

            val status = StalenessChecker.checkStaleness(tempDir, listOf(xmlFile))
            assertFalse(status.isStale)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testStaleWhenSourceIsNewer() {
        val tempDir = createTempDirectory("staletest").toFile()
        try {
            val xmlDir = File(tempDir, "build/test-results/test").apply { mkdirs() }
            val xmlFile = File(xmlDir, "TEST-FooTest.xml").apply {
                writeText("<testsuite name=\"FooTest\" tests=\"1\"/>")
                setLastModified(1000L)
            }

            val srcDir = File(tempDir, "src/main/kotlin").apply { mkdirs() }
            File(srcDir, "Foo.kt").apply {
                writeText("class Foo")
                setLastModified(5000L)
            }

            val status = StalenessChecker.checkStaleness(tempDir, listOf(xmlFile))
            assertTrue(status.isStale)
            assertTrue(status.staleReason?.contains("Foo.kt") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
