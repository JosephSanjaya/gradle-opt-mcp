package com.gradle.optimization.mcp.features.health.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.health.api.GradleHealthRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthFeatureImplTest {
    @Test
    fun testHealthCheckFallback() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Fallback test does not invoke connection")
            }
        }
        val tempDir = File.createTempFile("test_gradle_health", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        val config = GradleConfig(defaultProjectDir = tempDir.absolutePath)
        val feature = HealthFeatureImpl(fakePool, config)
        val result = feature.checkHealth(GradleHealthRequest(projectDir = tempDir.absolutePath))

        assertNotNull(result)
        assertEquals(tempDir.name, result.rootProjectName)
        assertEquals(0, result.subprojectCount)
        assertNotNull(result.gradleVersion)
        assertNotNull(result.javaVersion)
    }
}
