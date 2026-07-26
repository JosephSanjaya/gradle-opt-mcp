package com.gradle.optimization.mcp.features.linter.impl

import com.gradle.optimization.mcp.features.linter.api.PluginLinterRequest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginLinterFeatureImplTest {
    @Test
    fun `detects eager task creation and unsafe collection queries`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val buildScript = File(tempDir, "build.gradle.kts")
            buildScript.writeText(
                """
                plugins {
                    java
                }

                tasks.create("myEagerTask") {
                    doLast {
                        println("Running eager task")
                    }
                }

                tasks.withType<JavaCompile>().all {
                    options.encoding = "UTF-8"
                }
                """.trimIndent()
            )

            val impl = PluginLinterFeatureImpl()
            val result = impl.lintPlugins(PluginLinterRequest(tempDir.absolutePath))

            assertEquals(1, result.scannedFilesCount)
            assertEquals(2, result.totalViolations)
            assertEquals(2, result.violations.size)

            val eagerViolation = result.violations.first { it.ruleId == "EAGER_TASK_CREATION" }
            assertEquals("Eager Task Configuration", eagerViolation.category)
            assertEquals(5, eagerViolation.line)

            val collectionViolation = result.violations.first { it.ruleId == "UNSAFE_COLLECTION_QUERY" }
            assertEquals("Unsafe Collection Query", collectionViolation.category)
            assertEquals(11, collectionViolation.line)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `detects provider toString and task action project access`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val buildSrcDir = File(tempDir, "buildSrc/src/main/kotlin").apply { mkdirs() }
            val customPlugin = File(buildSrcDir, "MyCustomPlugin.kt")
            customPlugin.writeText(
                """
                package custom

                import org.gradle.api.Plugin
                import org.gradle.api.Project

                class MyCustomPlugin : Plugin<Project> {
                    override fun apply(project: Project) {
                        val propProvider = project.provider { "test" }
                        println(propProvider.toString())

                        project.tasks.register("myTask") {
                            doLast {
                                println(project.name)
                            }
                        }
                    }
                }
                """.trimIndent()
            )

            val impl = PluginLinterFeatureImpl()
            val result = impl.lintPlugins(PluginLinterRequest(tempDir.absolutePath))

            assertEquals(1, result.scannedFilesCount)
            assertEquals(2, result.totalViolations)
            assertTrue(result.violations.any { it.ruleId == "PROVIDER_TO_STRING" })
            assertTrue(result.violations.any { it.ruleId == "TASK_ACTION_PROJECT_ACCESS" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `detects bare provider toString and multiline doLast project member access`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "build.gradle.kts").writeText(
                """
                val provider = provider { "x" }
                println(provider.toString())

                tasks.register("t") {
                    doLast
                    {
                        println(project.version)
                    }
                }
                """.trimIndent()
            )

            val result = PluginLinterFeatureImpl().lintPlugins(PluginLinterRequest(tempDir.absolutePath))

            assertTrue(result.violations.any { it.ruleId == "PROVIDER_TO_STRING" })
            assertTrue(result.violations.any { it.ruleId == "TASK_ACTION_PROJECT_ACCESS" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `ignores project identifier without member access inside task action`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "build.gradle.kts").writeText(
                """
                tasks.register("t") {
                    doLast {
                        println("project is fine")
                        val projectName = "local"
                        println(projectName)
                    }
                }
                """.trimIndent()
            )

            val result = PluginLinterFeatureImpl().lintPlugins(PluginLinterRequest(tempDir.absolutePath))

            assertEquals(0, result.totalViolations)
            assertFalse(result.violations.any { it.ruleId == "TASK_ACTION_PROJECT_ACCESS" })
            assertTrue(result.summary.contains("Rules checked:"))
            assertTrue(result.summary.contains("Scanned roots:"))
            assertTrue(result.rulesChecked.contains("TASK_ACTION_PROJECT_ACCESS"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `caps violations by maxFindings and sets truncated`() {
        val tempDir = createTempDirectory().toFile()
        try {
            File(tempDir, "build.gradle.kts").writeText(
                """
                tasks.create("a") {}
                tasks.create("b") {}
                tasks.create("c") {}
                """.trimIndent()
            )

            val result = PluginLinterFeatureImpl().lintPlugins(
                PluginLinterRequest(projectDir = tempDir.absolutePath, maxFindings = 2)
            )

            assertEquals(3, result.totalViolations)
            assertEquals(2, result.violations.size)
            assertTrue(result.truncated)
            assertTrue(result.summary.contains("capped by maxFindings=2"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
