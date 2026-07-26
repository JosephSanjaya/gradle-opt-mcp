package com.gradle.optimization.mcp.features.declarative.impl

import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaRequest
import com.gradle.optimization.mcp.features.declarative.api.DeclarativeSchemaResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DeclarativeSchemaFeatureImplTest {
    @Test
    fun `has dcl reports software types actually used`() {
        withTempDir { tempDir ->
            File(tempDir, "build.gradle.dcl").writeText(
                "javaApplication {\n    mainClass = \"com.example.Main\"\n}\n"
            )

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertTrue(result.hasDeclarativeGradle)
            assertEquals(DeclarativeSchemaResult.STATUS_HAS_DCL, result.status)
            assertEquals(1, result.declarativeFiles.size)
            assertEquals("build.gradle.dcl", result.declarativeFiles.first().filePath)
            assertEquals(":", result.declarativeFiles.first().subproject)
            assertTrue(result.declarativeFiles.first().elements.contains("javaApplication"))
            assertEquals(listOf("javaApplication"), result.registeredSoftwareTypes.map { it.name })
            assertTrue(result.exposedConfigurations.any { it.blockName == "javaApplication" })
            assertFalse(result.exposedConfigurations.any { it.blockName == "kotlinLibrary" })
        }
    }

    @Test
    fun `empty project is NOT_DECLARATIVE with no invented types`() {
        withTempDir { tempDir ->
            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertFalse(result.hasDeclarativeGradle)
            assertEquals(DeclarativeSchemaResult.STATUS_NOT_DECLARATIVE, result.status)
            assertEquals(0, result.declarativeFiles.size)
            assertTrue(result.registeredSoftwareTypes.isEmpty())
            assertTrue(result.exposedConfigurations.isEmpty())
            assertTrue(result.warnings.isEmpty())
            assertTrue(result.scannedRoots.contains("."))
            assertTrue(result.detectionMethod.contains("static"))
            assertTrue(result.summary.contains("not using Declarative Gradle", ignoreCase = true))
            assertFalse(result.recommendations.any { it.contains("Migrate remaining", ignoreCase = true) })
        }
    }

    @Test
    fun `module name containing declarative does not false positive`() {
        withTempDir { tempDir ->
            File(tempDir, "settings.gradle.kts").writeText(
                """
                rootProject.name = "demo"
                include(":features:declarative-schema")
                """.trimIndent()
            )
            File(tempDir, "features/declarative-schema").mkdirs()
            File(tempDir, "features/declarative-schema/build.gradle.kts").writeText("plugins { java }")

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertFalse(result.hasDeclarativeGradle)
            assertEquals(DeclarativeSchemaResult.STATUS_NOT_DECLARATIVE, result.status)
            assertTrue(result.registeredSoftwareTypes.isEmpty())
        }
    }

    @Test
    fun `ecosystem plugin without dcl is NO_DCL`() {
        withTempDir { tempDir ->
            File(tempDir, "settings.gradle.kts").writeText(
                """
                plugins {
                    id("org.gradle.experimental.java-ecosystem")
                }
                """.trimIndent()
            )

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertTrue(result.hasDeclarativeGradle)
            assertEquals(DeclarativeSchemaResult.STATUS_NO_DCL, result.status)
            assertTrue(result.registeredSoftwareTypes.isEmpty())
            assertTrue(result.warnings.any { it.contains("no .dcl", ignoreCase = true) })
        }
    }

    @Test
    fun `subprojectPath scopes scan to module directory`() {
        withTempDir { tempDir ->
            File(tempDir, "features/foo").mkdirs()
            File(tempDir, "features/bar").mkdirs()
            File(tempDir, "features/foo/build.gradle.dcl").writeText("javaLibrary {\n}\n")
            File(tempDir, "features/bar/build.gradle.dcl").writeText("javaApplication {\n}\n")

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(
                    projectDir = tempDir.absolutePath,
                    subprojectPath = ":features:foo"
                )
            )

            assertEquals(DeclarativeSchemaResult.STATUS_HAS_DCL, result.status)
            assertEquals(1, result.declarativeFiles.size)
            assertEquals("features/foo/build.gradle.dcl", result.declarativeFiles.first().filePath)
            assertEquals(":features:foo", result.declarativeFiles.first().subproject)
            assertEquals(listOf("features/foo"), result.scannedRoots)
            assertEquals(listOf("javaLibrary"), result.registeredSoftwareTypes.map { it.name })
        }
    }

    @Test
    fun `unknown subprojectPath fails closed`() {
        withTempDir { tempDir ->
            try {
                DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                    DeclarativeSchemaRequest(
                        projectDir = tempDir.absolutePath,
                        subprojectPath = ":missing:module"
                    )
                )
                fail("expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("subprojectPath"))
            }
        }
    }

    @Test
    fun `missing projectDir fails closed`() {
        try {
            DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = "/tmp/definitely-missing-dcl-proj-xyz")
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("does not exist"))
        }
    }

    @Test
    fun `skips build and gradle caches when walking`() {
        withTempDir { tempDir ->
            File(tempDir, "build/generated").mkdirs()
            File(tempDir, ".gradle/caches").mkdirs()
            File(tempDir, "build/generated/fake.dcl").writeText("javaApplication {}\n")
            File(tempDir, ".gradle/caches/fake.dcl").writeText("javaLibrary {}\n")
            File(tempDir, "src").mkdirs()
            File(tempDir, "app.dcl").writeText("javaApplication {\n    mainClass = \"A\"\n}\n")

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertEquals(1, result.declarativeFilesTotal)
            assertEquals("app.dcl", result.declarativeFiles.single().filePath)
        }
    }

    @Test
    fun `does not recommend migrate when no imperative scripts`() {
        withTempDir { tempDir ->
            File(tempDir, "build.gradle.dcl").writeText("javaLibrary {\n}\n")

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath)
            )

            assertFalse(result.recommendations.any { it.contains("Migrate remaining", ignoreCase = true) })
            assertTrue(result.warnings.isEmpty())
        }
    }

    @Test
    fun `caps files and sets truncated`() {
        withTempDir { tempDir ->
            repeat(3) { index ->
                File(tempDir, "m$index.dcl").writeText("javaLibrary {}\n")
            }

            val result = DeclarativeSchemaFeatureImpl().inspectDeclarativeSchemas(
                DeclarativeSchemaRequest(projectDir = tempDir.absolutePath, maxFiles = 1)
            )

            assertTrue(result.truncated)
            assertEquals(3, result.declarativeFilesTotal)
            assertEquals(1, result.declarativeFiles.size)
        }
    }

    @Test
    fun `gradle path helpers normalize separators`() {
        assertEquals(":", DeclarativeSchemaFeatureImpl.normalizeGradlePath(""))
        assertEquals(":", DeclarativeSchemaFeatureImpl.normalizeGradlePath(":"))
        assertEquals(":features:foo", DeclarativeSchemaFeatureImpl.normalizeGradlePath("features:foo"))
        assertEquals(
            "features${File.separator}foo",
            DeclarativeSchemaFeatureImpl.gradlePathToRelativeDir(":features:foo")
        )
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = File.createTempFile("dcl-test-", "").apply {
            delete()
            check(mkdir())
        }
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
