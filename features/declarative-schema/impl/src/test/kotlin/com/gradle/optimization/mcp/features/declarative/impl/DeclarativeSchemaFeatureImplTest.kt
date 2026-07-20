package com.gradle.optimization.mcp.features.declarative.impl

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeclarativeSchemaFeatureImplTest {
    @Test
    fun testInspectDeclarativeSchemasWithDclFiles() {
        val feature = DeclarativeSchemaFeatureImpl()
        val tempDir = File.createTempFile("dcl-test-proj", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        File(tempDir, "build.gradle.dcl").apply {
            writeText("javaApplication {\n    mainClass = \"com.example.Main\"\n}\n")
            deleteOnExit()
        }

        val result = feature.inspectDeclarativeSchemas(
            DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
        )

        assertNotNull(result)
        assertTrue(result.hasDeclarativeGradle)
        assertEquals(1, result.declarativeFiles.size)
        assertEquals("build.gradle.dcl", result.declarativeFiles.first().filePath)
        assertTrue(result.declarativeFiles.first().elements.contains("javaApplication"))
        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test
    fun testInspectDeclarativeSchemasEmptyProject() {
        val feature = DeclarativeSchemaFeatureImpl()
        val tempDir = File.createTempFile("empty-test-proj", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        val result = feature.inspectDeclarativeSchemas(
            DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
        )

        assertNotNull(result)
        assertEquals(0, result.declarativeFiles.size)
        assertTrue(result.recommendations.isNotEmpty())
    }
}
