package com.gradle.optimization.mcp.features.dryrun.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dryrun.api.DryRunRequest
import com.gradle.optimization.mcp.features.dryrun.api.DryRunTaskNode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DryRunFeatureImplTest {
    @Test
    fun `rejects missing project directory`() {
        val feature = DryRunFeatureImpl(fakePool())
        val missing = File("/tmp/definitely-missing-dry-run-dir-${System.nanoTime()}")

        val error = assertFailsWith<IllegalArgumentException> {
            feature.analyzeDryRun(DryRunRequest(projectDir = missing.absolutePath, tasks = listOf("help")))
        }
        assertTrue(error.message!!.contains("does not exist"))
    }

    @Test
    fun `parses dry-run SKIPPED lines as scheduled task paths`() {
        val output = """
            :app:compileKotlin SKIPPED
            :app:jar SKIPPED
            :lib:compileKotlin SKIPPED
            Some other noise
            :lib:test SKIPPED
        """.trimIndent()

        val paths = DryRunOutputParser.parseTaskPaths(output)
        assertEquals(
            listOf(":app:compileKotlin", ":app:jar", ":lib:compileKotlin", ":lib:test"),
            paths
        )
        assertFalse(paths.any { it.contains("SKIPPED") })
    }

    @Test
    fun `groups by module and leaf type`() {
        val paths = listOf(
            ":app:compileKotlin",
            ":app:jar",
            ":lib:compileKotlin",
            ":features:dry-run:impl:compileKotlin"
        )

        val byModule = DryRunOutputParser.countByModule(paths)
        assertEquals(":app", byModule.first().name)
        assertEquals(2, byModule.first().count)
        assertEquals(1, byModule.first { it.name == ":lib" }.count)
        assertEquals(1, byModule.first { it.name == ":features:dry-run:impl" }.count)

        val byLeaf = DryRunOutputParser.countByLeafType(paths)
        assertEquals("compileKotlin", byLeaf.first().name)
        assertEquals(3, byLeaf.first().count)
        assertEquals(1, byLeaf.first { it.name == "jar" }.count)
    }

    @Test
    fun `extracts What went wrong failure reason`() {
        val output = """
            FAILURE: Build failed with an exception.

            * What went wrong:
            Task 'nope' not found in root project 'demo'.

            * Try:
            Run gradlew tasks
        """.trimIndent()

        val reason = DryRunOutputParser.extractFailureReason(output)
        assertEquals("Task 'nope' not found in root project 'demo'.", reason)
    }

    @Test
    fun `extractFailureReason returns null without marker`() {
        assertNull(DryRunOutputParser.extractFailureReason("BUILD SUCCESSFUL"))
    }

    @Test
    fun `scheduled status constant is not SKIPPED`() {
        assertEquals("SCHEDULED", DryRunTaskNode.STATUS_SCHEDULED)
        assertTrue(DryRunTaskNode.STATUS_SCHEDULED != "SKIPPED")
    }

    private fun fakePool(): GradleConnectionPool = object : GradleConnectionPool {
        override fun <T> withConnection(
            projectDir: File,
            action: (org.gradle.tooling.ProjectConnection) -> T
        ): T = error("Not needed for unit test")
    }
}
