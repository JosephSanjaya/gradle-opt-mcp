package com.gradle.optimization.mcp.features.isolation.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheProblem
import com.gradle.optimization.mcp.features.isolation.api.IsolationCheckRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.tooling.ProjectConnection

class ProjectIsolationFeatureImplTest {
    @Test
    fun missingDirectoryThrows() {
        val feature = ProjectIsolationFeatureImpl(unusedPool())
        assertFailsWith<IllegalArgumentException> {
            feature.checkProjectIsolation(IsolationCheckRequest("/non/existent/directory"))
        }
    }

    @Test
    fun nonGradleDirectoryThrows() {
        val tmp = Files.createTempDirectory("isolation-nongradle").toFile()
        try {
            val feature = ProjectIsolationFeatureImpl(unusedPool())
            assertFailsWith<IllegalArgumentException> {
                feature.checkProjectIsolation(IsolationCheckRequest(tmp.absolutePath))
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun incubatingProjectAccessorsAreNotViolations() {
        assertNull(
            ProjectIsolationViolationMapper.classify(
                "Project accessors are an incubating feature."
            )
        )
        assertTrue(
            ProjectIsolationViolationMapper.isIncubatingProjectAccessorNoise(
                "Generating type-safe project accessors is an incubating feature."
            )
        )
        val console = ProjectIsolationViolationMapper.fromConsoleOutput(
            """
            Project accessors are an incubating feature.
            Generating type-safe project accessors is an incubating feature.
            Project Accessors enabled
            """.trimIndent()
        )
        assertTrue(console.isEmpty(), "Incubating project accessors must not be violations: $console")
    }

    @Test
    fun classifiesRealIsolationPatterns() {
        assertEquals(
            "ILLEGAL_SIBLING_PLUGIN_ACCESS",
            ProjectIsolationViolationMapper.classify(
                "Project ':a' cannot access 'Project.plugins' on project ':b'"
            )
        )
        assertEquals(
            "ILLEGAL_ROOT_PROPERTY_ACCESS",
            ProjectIsolationViolationMapper.classify(
                "Project ':lib' cannot access rootProject at configuration time"
            )
        )
        assertEquals(
            "ILLEGAL_CROSS_PROJECT_CLOSURE",
            ProjectIsolationViolationMapper.classify(
                "Using subprojects {} breaks project isolation"
            )
        )
        assertEquals(
            "CROSS_PROJECT_ACCESS",
            ProjectIsolationViolationMapper.classify(
                "Cannot access project ':api' from project ':app'"
            )
        )
    }

    @Test
    fun reportProblemsMapToStructuredViolationsWithLocation() {
        val problems = listOf(
            ConfigCacheProblem(
                message = "Project ':app' cannot access 'Project.plugins' on project ':lib'",
                location = "Build file 'app/build.gradle.kts': line 12",
                documentationLink = "https://docs.gradle.org/isolation"
            ),
            ConfigCacheProblem(
                message = "Project accessors are an incubating feature.",
                location = "settings.gradle.kts"
            )
        )
        val violations = ProjectIsolationViolationMapper.fromReportProblems(problems)
        assertEquals(1, violations.size)
        assertEquals("ILLEGAL_SIBLING_PLUGIN_ACCESS", violations[0].violationType)
        assertEquals("app/build.gradle.kts", violations[0].sourceFile)
        assertEquals(12, violations[0].lineNumber)
        assertEquals("Build file 'app/build.gradle.kts': line 12", violations[0].location)
    }

    @Test
    fun toolingFailureWithoutViolationsIsNotIsolatedSuccess() {
        val tmp = Files.createTempDirectory("isolation-tooling-fail").toFile()
        try {
            File(tmp, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            val feature = ProjectIsolationFeatureImpl(
                object : GradleConnectionPool {
                    override fun <T> withConnection(
                        projectDir: File,
                        action: (ProjectConnection) -> T
                    ): T {
                        error("simulated tooling failure")
                    }
                }
            )
            val result = feature.checkProjectIsolation(IsolationCheckRequest(tmp.absolutePath))
            assertFalse(result.success)
            assertFalse(result.isIsolated)
            assertEquals(0, result.totalViolationCount)
            assertTrue(result.failureReason!!.contains("simulated tooling failure"))
            assertTrue(result.summary.contains("failed", ignoreCase = true))
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun unusedPool(): GradleConnectionPool = object : GradleConnectionPool {
        override fun <T> withConnection(projectDir: File, action: (ProjectConnection) -> T): T {
            error("Not needed")
        }
    }
}
