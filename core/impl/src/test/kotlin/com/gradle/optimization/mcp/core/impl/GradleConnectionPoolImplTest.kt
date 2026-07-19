package com.gradle.optimization.mcp.core.impl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class GradleConnectionPoolImplTest {
    @Test
    fun testPoolInitializationAndClose() {
        val pool = GradleConnectionPoolImpl()
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gradle-test-project")
        tempDir.mkdirs()

        pool.use { connectionPool ->
            assertNotNull(connectionPool)
        }
    }
}
