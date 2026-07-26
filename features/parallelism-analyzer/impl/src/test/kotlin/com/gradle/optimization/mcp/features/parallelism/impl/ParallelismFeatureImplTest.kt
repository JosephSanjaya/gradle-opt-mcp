package com.gradle.optimization.mcp.features.parallelism.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisRequest
import com.gradle.optimization.mcp.features.parallelism.api.ParallelismAnalysisResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelismFeatureImplTest {
    @Test
    fun `rejects missing project directory`() {
        val feature = ParallelismFeatureImpl(fakePool())
        val missing = File("/tmp/definitely-missing-parallelism-dir-${System.nanoTime()}")

        val error = assertFailsWith<IllegalArgumentException> {
            feature.analyzeParallelizationBottlenecks(
                ParallelismAnalysisRequest(projectDir = missing.absolutePath, tasks = listOf("help"))
            )
        }
        assertTrue(error.message!!.contains("does not exist"))
    }

    @Test
    fun `rejects non-Gradle directory`() {
        val feature = ParallelismFeatureImpl(fakePool())
        val dir = kotlin.io.path.createTempDirectory("parallelism-nongradle").toFile()
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                feature.analyzeParallelizationBottlenecks(
                    ParallelismAnalysisRequest(projectDir = dir.absolutePath, tasks = listOf("help"))
                )
            }
            assertTrue(error.message!!.contains("Not a Gradle project"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `parses dry-run SKIPPED lines as task paths`() {
        val output = """
            :app:compileKotlin SKIPPED
            :app:jar SKIPPED
            :lib:compileKotlin SKIPPED
            Some other noise
            :lib:test SKIPPED
        """.trimIndent()

        val paths = ParallelismOutputParser.parseTaskPaths(output)
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
            ":features:parallelism-analyzer:impl:compileKotlin"
        )

        val byModule = ParallelismOutputParser.countByModule(paths)
        assertEquals(":app", byModule.first().name)
        assertEquals(2, byModule.first().count)

        val byLeaf = ParallelismOutputParser.countByLeafType(paths)
        assertEquals("compileKotlin", byLeaf.first().name)
        assertEquals(3, byLeaf.first().count)
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

        val reason = ParallelismOutputParser.extractFailureReason(output)
        assertEquals("Task 'nope' not found in root project 'demo'.", reason)
    }

    @Test
    fun `extractFailureReason returns null without marker`() {
        assertNull(ParallelismOutputParser.extractFailureReason("BUILD SUCCESSFUL"))
    }

    @Test
    fun `recommended tasks documented for agents`() {
        assertEquals(listOf("classes", "assemble", "build"), ParallelismAnalysisRequest.RECOMMENDED_TASKS)
        assertTrue(ParallelismAnalysisResult.CAVEAT.contains("Heuristic"))
        assertTrue(ParallelismAnalysisResult.CAVEAT.contains("no Amdahl"))
    }

    private fun fakePool(): GradleConnectionPool = object : GradleConnectionPool {
        override fun <T> withConnection(
            projectDir: File,
            action: (org.gradle.tooling.ProjectConnection) -> T
        ): T = error("Not needed for unit test")
    }
}
