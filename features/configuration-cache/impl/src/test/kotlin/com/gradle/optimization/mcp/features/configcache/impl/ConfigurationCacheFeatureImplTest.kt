package com.gradle.optimization.mcp.features.configcache.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ConfigurationCacheFeatureImplTest {
    @Test
    fun testInitialization() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed")
            }
        }
        val feature = ConfigurationCacheFeatureImpl(fakePool)
        assertNotNull(feature)
    }

    @Test
    fun testNonExistentDirectoryThrows() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed")
            }
        }
        val feature = ConfigurationCacheFeatureImpl(fakePool)
        assertFailsWith<IllegalArgumentException> {
            feature.auditConfigurationCacheInputs(ConfigCacheAuditRequest("/non/existent/directory"))
        }
    }

    @Test
    fun testParseInputViolations() {
        val fakePool = object : GradleConnectionPool {
            override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
                error("Not needed")
            }
        }
        val feature = ConfigurationCacheFeatureImpl(fakePool)
        val rawLogs = """
            - Read System.getenv("API_KEY") at build.gradle.kts:12
            - Read System.getProperty("os.name") at build.gradle.kts:15
            - Directory content listing via File.listFiles() at build.gradle.kts:20
            - Direct file content read via readText() at build.gradle.kts:25
        """.trimIndent()

        val violations = feature.parseInputViolations(rawLogs)
        assertEquals(4, violations.size)
        assertEquals("ENVIRONMENT_VARIABLE", violations[0].inputType)
        assertEquals("SYSTEM_PROPERTY", violations[1].inputType)
        assertEquals("DIRECTORY_LISTING", violations[2].inputType)
        assertEquals("FILE_READ", violations[3].inputType)
    }
}
