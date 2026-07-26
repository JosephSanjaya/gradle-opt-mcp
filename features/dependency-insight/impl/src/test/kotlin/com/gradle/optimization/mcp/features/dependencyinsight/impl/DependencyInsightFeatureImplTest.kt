package com.gradle.optimization.mcp.features.dependencyinsight.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DependencyInsightFeatureImplTest {
    @Test
    fun testDependencyInsightInitialization() {
        val feature = DependencyInsightFeatureImpl(unusedPool())
        assertNotNull(feature)
    }

    @Test
    fun testInvalidInputsThrow() {
        val feature = DependencyInsightFeatureImpl(unusedPool())
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyInsight(
                DependencyInsightRequest("", "compileClasspath", "kotlinx-coroutines-core")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyInsight(
                DependencyInsightRequest("/tmp", "", "kotlinx-coroutines-core")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyInsight(
                DependencyInsightRequest("/tmp", "compileClasspath", "")
            )
        }
    }

    @Test
    fun testNormalizeModulePath() {
        assertEquals(":", DependencyInsightFeatureImpl.normalizeModulePath(""))
        assertEquals(":", DependencyInsightFeatureImpl.normalizeModulePath("  "))
        assertEquals(":", DependencyInsightFeatureImpl.normalizeModulePath(":"))
        assertEquals(":core:api", DependencyInsightFeatureImpl.normalizeModulePath("core:api"))
        assertEquals(":core:api", DependencyInsightFeatureImpl.normalizeModulePath(":core:api"))
    }

    @Test
    fun testInsightTaskPath() {
        assertEquals(":mcpDependencyInsight", DependencyInsightFeatureImpl.insightTaskPath(":"))
        assertEquals(
            ":core:api:mcpDependencyInsight",
            DependencyInsightFeatureImpl.insightTaskPath(":core:api")
        )
    }

    @Test
    fun testParseHappyPathInsight() {
        val raw = """
            org.jetbrains.kotlin:kotlin-stdlib:2.4.10
              Variant jvmApiElements:
                | Attribute Name                     | Provided     | Requested    |
               Selection reasons:
                  - By conflict resolution: between versions 2.4.10 and 2.1.20

            org.jetbrains.kotlin:kotlin-stdlib:2.4.10
            \--- compileClasspath

            org.jetbrains.kotlin:kotlin-stdlib:2.1.20 -> 2.4.10
            +--- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1
            |    \--- compileClasspath
            \--- org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1 (*)

            (*) - Indicates repeated occurrences of a transitive dependency subtree.
        """.trimIndent()

        val parsed = DependencyInsightParser.parse(raw, maxPaths = 5, maxReasons = 5)
        assertTrue(parsed.found)
        assertEquals("2.4.10", parsed.selectedVersion)
        assertEquals(
            listOf("By conflict resolution: between versions 2.4.10 and 2.1.20"),
            parsed.reasons
        )
        assertEquals(2, parsed.paths.size)
        assertTrue(parsed.paths[0].contains("\\--- compileClasspath"))
        assertTrue(parsed.paths[1].contains("2.1.20 -> 2.4.10"))
        assertNull(parsed.notFoundMessage)
    }

    @Test
    fun testParseNotFoundIsFailClosed() {
        val raw = "No dependencies matching given input were found in configuration ':core:api:compileClasspath'"
        val parsed = DependencyInsightParser.parse(raw, maxPaths = 5, maxReasons = 5)
        assertFalse(parsed.found)
        assertNull(parsed.selectedVersion)
        assertTrue(parsed.reasons.isEmpty())
        assertTrue(parsed.paths.isEmpty())
        assertEquals(raw, parsed.notFoundMessage)
    }

    @Test
    fun testParseCapsPathsAndReasons() {
        val raw = """
            com.example:lib:1.0.0
               Selection reasons:
                  - reason-one
                  - reason-two
                  - reason-three

            com.example:lib:1.0.0
            \--- compileClasspath

            com.example:lib:0.9.0 -> 1.0.0
            \--- otherClasspath
        """.trimIndent()

        val parsed = DependencyInsightParser.parse(raw, maxPaths = 1, maxReasons = 2)
        assertEquals(2, parsed.reasons.size)
        assertEquals(1, parsed.paths.size)
    }

    @Test
    fun testParseRejectsNonGavNoise() {
        val raw = "Unknown configuration 'doesNotExist' on project ':core:api'."
        val parsed = DependencyInsightParser.parse(raw, maxPaths = 5, maxReasons = 5)
        assertFalse(parsed.found)
        assertNull(parsed.selectedVersion)
    }

    @Test
    fun testExtractBuildFailureReasonUnknownConfig() {
        val diagnostics = """
            FAILURE: Build failed with an exception.

            * What went wrong:
            A problem occurred configuring project ':core:api'.
            > Unknown configuration 'doesNotExist' on project ':core:api'

            * Try:
            > Run with --stacktrace
        """.trimIndent()
        assertEquals(
            "Unknown configuration 'doesNotExist' on project ':core:api'",
            DependencyInsightParser.extractBuildFailureReason(diagnostics)
        )
    }

    private fun unusedPool(): GradleConnectionPool = object : GradleConnectionPool {
        override fun <T> withConnection(
            projectDir: File,
            action: (org.gradle.tooling.ProjectConnection) -> T
        ): T {
            error("Not needed")
        }
    }
}
