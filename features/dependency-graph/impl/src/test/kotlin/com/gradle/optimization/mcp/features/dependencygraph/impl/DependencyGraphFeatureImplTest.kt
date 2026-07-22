package com.gradle.optimization.mcp.features.dependencygraph.impl

import com.gradle.optimization.mcp.core.api.GradleConfig
import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencygraph.api.GradleDepsRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DependencyGraphFeatureImplTest {
    @Test
    fun testDependencyGraphInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for initialization test")
            }
        }
        val config = GradleConfig(defaultProjectDir = System.getProperty("user.dir"))
        val feature = DependencyGraphFeatureImpl(fakePool, config)
        assertNotNull(feature)
    }

    @Test
    fun testNonExistentDirectoryThrows() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed")
            }
        }
        val config = GradleConfig(defaultProjectDir = System.getProperty("user.dir"))
        val feature = DependencyGraphFeatureImpl(fakePool, config)
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyGraph(GradleDepsRequest(projectDir = "/path/that/does/not/exist/xyz123"))
        }
    }
}
