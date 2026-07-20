package com.gradle.optimization.mcp.features.parallelism.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class ParallelismFeatureImplTest {
    @Test
    fun testParallelismFeatureInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for mock unit test")
            }
        }
        val feature = ParallelismFeatureImpl(fakePool)
        assertNotNull(feature)
    }
}
