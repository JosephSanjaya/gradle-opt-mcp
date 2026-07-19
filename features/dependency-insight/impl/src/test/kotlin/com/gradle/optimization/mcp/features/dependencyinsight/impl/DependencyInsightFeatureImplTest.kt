package com.gradle.optimization.mcp.features.dependencyinsight.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.dependencyinsight.api.DependencyInsightRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DependencyInsightFeatureImplTest {
    @Test
    fun testDependencyInsightInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed for initialization test")
            }
        }
        val feature = DependencyInsightFeatureImpl(fakePool)
        assertNotNull(feature)
    }

    @Test
    fun testInvalidInputsThrow() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed")
            }
        }
        val feature = DependencyInsightFeatureImpl(fakePool)
        assertFailsWith<IllegalArgumentException> {
            feature.getDependencyInsight(DependencyInsightRequest("", "compileClasspath", "kotlinx-coroutines-core"))
        }
    }
}
