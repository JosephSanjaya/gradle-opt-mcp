package com.gradle.optimization.mcp.features.testsummary.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryRequest
import com.gradle.optimization.mcp.features.testsummary.api.GradleTestSummaryResult
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSummaryFeatureImplTest {
    @Test
    fun noReportsIsMissingNotFresh() {
        val tempDir = createTempDirectory("testsum-empty").toFile()
        try {
            val api = TestSummaryFeatureImpl(GradleConfig(defaultProjectDir = tempDir.absolutePath))
            val result = api.getTestSummary(GradleTestSummaryRequest(projectDir = tempDir.absolutePath))

            assertEquals(GradleTestSummaryResult.STATUS_NO_REPORTS, result.reportsStatus)
            assertNull(result.isStale)
            assertNotNull(result.guidance)
            assertTrue(result.guidance!!.contains("Run tests"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun moduleFilterEmptyDistinctFromNoReports() {
        val tempDir = createTempDirectory("testsum-filter").toFile()
        try {
            writePassingSuite(tempDir, moduleRel = "features/runner", suiteName = "GreenTest")

            val api = TestSummaryFeatureImpl(GradleConfig(defaultProjectDir = tempDir.absolutePath))
            val result = api.getTestSummary(
                GradleTestSummaryRequest(
                    projectDir = tempDir.absolutePath,
                    modulePath = ":features:nonexistent"
                )
            )

            assertEquals(GradleTestSummaryResult.STATUS_MODULE_FILTER_EMPTY, result.reportsStatus)
            assertNull(result.isStale)
            assertTrue(result.guidance!!.contains("modulePath"))
            assertTrue(result.guidance!!.contains("Run tests"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun stalenessScopedToFilteredXmls() {
        val tempDir = createTempDirectory("testsum-stale").toFile()
        try {
            val runnerXml = writePassingSuite(tempDir, moduleRel = "features/runner", suiteName = "RunnerTest")
            runnerXml.setLastModified(1_000L)

            val healthXml = writePassingSuite(tempDir, moduleRel = "features/health", suiteName = "HealthTest")
            healthXml.setLastModified(10_000L)

            File(tempDir, "src/main/kotlin/Foo.kt").apply {
                parentFile.mkdirs()
                writeText("class Foo")
                setLastModified(5_000L)
            }

            val api = TestSummaryFeatureImpl(GradleConfig(defaultProjectDir = tempDir.absolutePath))

            val runnerOnly = api.getTestSummary(
                GradleTestSummaryRequest(
                    projectDir = tempDir.absolutePath,
                    modulePath = ":features:runner"
                )
            )
            assertEquals(GradleTestSummaryResult.STATUS_FOUND, runnerOnly.reportsStatus)
            assertEquals(true, runnerOnly.isStale)

            val healthOnly = api.getTestSummary(
                GradleTestSummaryRequest(
                    projectDir = tempDir.absolutePath,
                    modulePath = ":features:health"
                )
            )
            assertEquals(GradleTestSummaryResult.STATUS_FOUND, healthOnly.reportsStatus)
            assertEquals(false, healthOnly.isStale)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun collapsesAllGreenSuitesWhenFailuresExist() {
        val tempDir = createTempDirectory("testsum-collapse").toFile()
        try {
            writePassingSuite(tempDir, moduleRel = "features/a", suiteName = "GreenSuite")
            writeFailingSuite(tempDir, moduleRel = "features/b", suiteName = "FailSuite")

            val api = TestSummaryFeatureImpl(GradleConfig(defaultProjectDir = tempDir.absolutePath))
            val result = api.getTestSummary(GradleTestSummaryRequest(projectDir = tempDir.absolutePath))

            assertEquals(1, result.failedCount)
            assertEquals(1, result.collapsedGreenSuiteCount)
            assertEquals(1, result.suiteSummaries.size)
            assertEquals("FailSuite", result.suiteSummaries.single().suiteName)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writePassingSuite(root: File, moduleRel: String, suiteName: String): File {
        val dir = File(root, "$moduleRel/build/test-results/test").apply { mkdirs() }
        return File(dir, "TEST-$suiteName.xml").apply {
            writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="$suiteName" tests="1" failures="0" errors="0" skipped="0" time="0.1">
                  <testcase name="ok" classname="$suiteName" time="0.1"/>
                </testsuite>
                """.trimIndent().trimStart() + "\n"
            )
        }
    }

    private fun writeFailingSuite(root: File, moduleRel: String, suiteName: String): File {
        val dir = File(root, "$moduleRel/build/test-results/test").apply { mkdirs() }
        // Build without trimIndent + multiline interpolation (that zeroes indent and breaks <?xml).
        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<testsuite name="$suiteName" tests="1" failures="1" errors="0" skipped="0" time="0.1">"""
            )
            appendLine("""  <testcase name="bad" classname="$suiteName" time="0.1">""")
            appendLine("""    <failure message="boom" type="java.lang.AssertionError">""")
            appendLine("java.lang.AssertionError: boom")
            appendLine("at $suiteName.bad($suiteName.kt:3)")
            appendLine("at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:55)")
            appendLine("    </failure>")
            appendLine("  </testcase>")
            appendLine("</testsuite>")
        }
        return File(dir, "TEST-$suiteName.xml").apply { writeText(xml) }
    }
}
