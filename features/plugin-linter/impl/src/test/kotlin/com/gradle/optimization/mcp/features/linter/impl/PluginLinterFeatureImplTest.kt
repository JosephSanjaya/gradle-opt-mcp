package com.gradle.optimization.mcp.features.linter.impl

import com.gradle.optimization.mcp.features.linter.api.PluginLinterRequest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertEquals(2, result.violations.size)
            assertTrue(result.violations.any { it.ruleId == "PROVIDER_TO_STRING" })
            assertTrue(result.violations.any { it.ruleId == "TASK_ACTION_PROJECT_ACCESS" })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
