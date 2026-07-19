package com.gradle.optimization.mcp.features.dryrun.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class DryRunFeatureImplTest {
    @Test
    fun testDryRunInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for mock unit test")
            }
        }
        val feature = DryRunFeatureImpl(fakePool)
        assertNotNull(feature)
    }
}
