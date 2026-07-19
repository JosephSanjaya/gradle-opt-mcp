package com.gradle.optimization.mcp.features.isolation.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class ProjectIsolationFeatureImplTest {
    @Test
    fun testIsolationInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for mock unit test")
            }
        }
        val feature = ProjectIsolationFeatureImpl(fakePool)
        assertNotNull(feature)
    }
}
