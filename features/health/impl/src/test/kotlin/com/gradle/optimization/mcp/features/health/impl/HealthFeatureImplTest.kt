package com.gradle.optimization.mcp.features.health.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.health.api.GradleHealthRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthFeatureImplTest {
    @Test
    fun testFailsClosedOnToolingFailure() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(
                projectDir: File,
                action: (org.gradle.tooling.ProjectConnection) -> T
            ): T {
                error("Tooling API unavailable")
            }
        }
        val tempDir = kotlin.io.path.createTempDirectory("test_gradle_health").toFile().apply {
            File(this, "settings.gradle.kts").writeText("rootProject.name = \"demo\"")
            deleteOnExit()
        }

        val feature = HealthFeatureImpl(fakePool)
        val ex = assertFailsWith<IllegalStateException> {
            feature.checkHealth(GradleHealthRequest(projectDir = tempDir.absolutePath))
        }
        assertTrue(ex.message!!.contains("Tooling API unavailable"))
    }

    @Test
    fun testFailsClosedOnNonGradleDirectory() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(
                projectDir: File,
                action: (org.gradle.tooling.ProjectConnection) -> T
            ): T {
                error("should not connect")
            }
        }
        val tempDir = kotlin.io.path.createTempDirectory("test_gradle_health_empty").toFile().apply {
            deleteOnExit()
        }

        val feature = HealthFeatureImpl(fakePool)
        val ex = assertFailsWith<IllegalArgumentException> {
            feature.checkHealth(GradleHealthRequest(projectDir = tempDir.absolutePath))
        }
        assertTrue(ex.message!!.contains("Not a Gradle project"))
    }

    @Test
    fun testRequiresProjectDir() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(
                projectDir: File,
                action: (org.gradle.tooling.ProjectConnection) -> T
            ): T {
                error("should not connect")
            }
        }
        val feature = HealthFeatureImpl(fakePool)
        assertFailsWith<IllegalArgumentException> {
            feature.checkHealth(GradleHealthRequest(projectDir = "   "))
        }
    }
}
