package com.gradle.optimization.mcp.features.dependencygraph.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencygraph.api.DependencyNode
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import com.gradle.optimization.mcp.features.dependencygraph.api.SelectionReasonInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencyGraphFeatureImplTest {
    private val fakePool = object : GradleConnectionPool {
        override fun <T> withConnection(
            projectDir: File,
            action: (org.gradle.tooling.ProjectConnection) -> T
        ): T {
            error("Not needed for unit tests")
        }
    }

    private val feature = DependencyGraphFeatureImpl(fakePool)

    @Test
    fun testDependencyGraphInitialization() {
        assertNotNull(feature)
    }

    @Test
    fun testNonExistentDirectoryThrows() {
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyGraph(GradleDepsRequest(projectDir = "/path/that/does/not/exist/xyz123"))
        }
    }

    @Test
    fun testBlankProjectDirThrows() {
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyGraph(GradleDepsRequest(projectDir = ""))
        }
    }

    @Test
    fun testUnknownModulePathRejected() {
        val output = """
            MCP_DEP_PROJECTS:[":",":core",":server"]
            MCP_DEP_ERR:{"error":"Unknown modulePath: ':missing'. Known modules: :, :core, :server"}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            feature.parseOutput(
                projectDir = "/tmp/proj",
                rawOutput = output,
                rawErr = "",
                request = GradleDepsRequest(projectDir = "/tmp/proj", modulePath = ":missing")
            )
        }
    }

    @Test
    fun testSummaryFirstCapsAndGroupsConflicts() {
        val nodes = (1..5).map { i ->
            DependencyNode(
                modulePath = ":lib",
                configuration = "compileClasspath",
                group = "com.example",
                name = "lib-a",
                requestedVersion = "1.$i",
                resolvedVersion = "2.0",
                isDirect = i <= 2,
                isTransitive = i > 2,
                hasConflict = true,
                selectionReason = SelectionReasonInfo(
                    descriptions = listOf("conflict resolution"),
                    conflictResolution = true
                )
            )
        } + DependencyNode(
            modulePath = ":lib",
            configuration = "compileClasspath",
            group = "com.example",
            name = "lib-b",
            requestedVersion = "1.0",
            resolvedVersion = "1.0",
            isDirect = true,
            isTransitive = false,
            hasConflict = false
        )

        val result = DependencyGraphSummarizer.summarize(
            projectDir = "/tmp/proj",
            nodes = nodes,
            configurationsScanned = listOf("compileClasspath"),
            request = GradleDepsRequest(
                projectDir = "/tmp/proj",
                maxDependencies = 2,
                maxConflicts = 1
            ),
            errors = emptyList()
        )

        assertEquals(1, result.conflicts.size)
        assertEquals("lib-a", result.conflicts.first().name)
        assertTrue(result.conflicts.first().requestedVersions.size >= 2)
        assertEquals(2, result.dependencies.size)
        assertTrue(result.dependencies.all { it.isDirect })
        assertTrue(result.truncated)
        assertTrue(result.summary.contains("conflictGroups=1"))
        assertTrue(result.summary.contains("truncated=true"))
    }

    @Test
    fun testParseProjectComponentAndStructuredReason() {
        val output = """
            MCP_DEP_PROJECTS:[":",":features:api"]
            MCP_DEP_NODE:{"modulePath":":features:api","configuration":"compileClasspath","group":"project","name":":core:api","requestedVersion":"","resolvedVersion":"","isDirect":true,"isTransitive":false,"hasConflict":false,"selectionReason":{"descriptions":["requested"],"conflictResolution":false,"constrained":false,"forced":false,"expected":true},"componentKind":"project"}
            MCP_DEP_NODE:{"modulePath":":features:api","configuration":"compileClasspath","group":"org.jetbrains.kotlin","name":"kotlin-stdlib","requestedVersion":"2.0.0","resolvedVersion":"2.1.0","isDirect":false,"isTransitive":true,"hasConflict":true,"selectionReason":{"descriptions":["By conflict resolution"],"conflictResolution":true,"constrained":false,"forced":false,"expected":false},"componentKind":"module"}
        """.trimIndent()

        val result = feature.parseOutput(
            projectDir = "/tmp/proj",
            rawOutput = output,
            rawErr = "",
            request = GradleDepsRequest(
                projectDir = "/tmp/proj",
                modulePath = ":features:api",
                includeTransitive = true
            )
        )

        assertEquals(2, result.totalDependencies)
        val projectNode = result.dependencies.first { it.componentKind == "project" }
        assertEquals("project", projectNode.group)
        assertEquals(":core:api", projectNode.name)
        assertFalse(projectNode.name.contains("project :"))
        assertEquals(1, result.conflictGroupCount)
        assertTrue(result.conflicts.first().selectionReason.conflictResolution)
        assertEquals("By conflict resolution", result.conflicts.first().selectionReason.descriptions.first())
    }

    @Test
    fun testDefaultOmitsTransitiveFromList() {
        val nodes = listOf(
            DependencyNode(
                modulePath = ":",
                configuration = "compileClasspath",
                group = "a",
                name = "direct",
                requestedVersion = "1",
                resolvedVersion = "1",
                isDirect = true,
                isTransitive = false,
                hasConflict = false
            ),
            DependencyNode(
                modulePath = ":",
                configuration = "compileClasspath",
                group = "b",
                name = "trans",
                requestedVersion = "1",
                resolvedVersion = "1",
                isDirect = false,
                isTransitive = true,
                hasConflict = false
            )
        )
        val result = DependencyGraphSummarizer.summarize(
            projectDir = "/tmp",
            nodes = nodes,
            configurationsScanned = listOf("compileClasspath"),
            request = GradleDepsRequest(projectDir = "/tmp"),
            errors = emptyList()
        )
        assertEquals(1, result.totalDependencies)
        assertEquals(1, result.dependencies.size)
        assertEquals("direct", result.dependencies.first().name)
    }
}
