package com.gradle.optimization.mcp.features.configcache.impl

import com.gradle.optimization.mcp.core.api.GradleConnectionPool
import com.gradle.optimization.mcp.features.configcache.api.ConfigCacheAuditRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigurationCacheFeatureImplTest {
    @Test
    fun testNonExistentDirectoryThrows() {
        val feature = ConfigurationCacheFeatureImpl(unusedPool())
        assertFailsWith<IllegalArgumentException> {
            feature.auditConfigurationCacheInputs(ConfigCacheAuditRequest("/non/existent/directory"))
        }
    }

    @Test
    fun testParseReportHtmlExtractsInputsAndCounts() {
        val html = """
            <html><body>
            <script type="application/json">
            {"diagnostics":[
              {"trace":[{"kind":"BuildLogic","location":"plugin class 'DemoPlugin'"}],
               "input":[{"text":"environment variable "},{"name":"CI"}],
               "documentationLink":"https://docs.gradle.org/cc-env"},
              {"trace":[{"kind":"Project","path":":app"}],
               "input":[{"text":"system property "},{"name":"os.name"}]},
              {"trace":[{"kind":"BuildLogicClass","type":"Catalog"}],
               "input":[{"text":"file "},{"name":"gradle/libs.versions.toml"}]},
              {"trace":[{"kind":"Project","path":":app"}],
               "input":[{"text":"Gradle property "},{"name":"kotlin.session"}]}
            ],
            "totalProblemCount":0,
            "uniqueProblemCount":0,
            "overflownProblemCount":0,
            "buildName":"build ':'",
            "requestedTasks":":help",
            "cacheAction":"storing",
            "cacheActionDescription":[{"text":"Calculating task graph as no cached configuration is available for tasks: :help"}],
            "documentationLink":"https://docs.gradle.org/configuration_cache.html"}
            </script>
            </body></html>
        """.trimIndent()

        val parsed = ConfigurationCacheReportParser.parseReportHtml(html)
        assertNotNull(parsed)
        assertEquals("storing", parsed.cacheAction)
        assertEquals(listOf(":help"), parsed.requestedTasks)
        assertEquals(4, parsed.inputs.size)
        assertTrue(parsed.inputCounts.any { it.inputType == "ENVIRONMENT_VARIABLE" && it.count == 1 })
        assertTrue(parsed.inputCounts.any { it.inputType == "GRADLE_PROPERTY" && it.count == 1 })

        val notable = ConfigurationCacheReportParser.selectNotableInputs(parsed.inputs, maxNotable = 10)
        assertTrue(notable.any { it.inputType == "ENVIRONMENT_VARIABLE" && it.inputName == "CI" })
        assertTrue(notable.none { it.inputType == "GRADLE_PROPERTY" })
        assertEquals("plugin class 'DemoPlugin'", notable.first { it.inputName == "CI" }.location)
        assertEquals(
            "providers.environmentVariable(\"KEY\")",
            notable.first { it.inputName == "CI" }.recommendedRefactoring
        )
    }

    @Test
    fun testFindNewestReportPrefersLatestFile() {
        val root = Files.createTempDirectory("cc-report-test").toFile()
        val olderDir = File(root, "build/reports/configuration-cache/old/entry").apply { mkdirs() }
        val newerDir = File(root, "build/reports/configuration-cache/new/entry").apply { mkdirs() }
        val older = File(olderDir, "configuration-cache-report.html").apply {
            writeText("""{"diagnostics":[],"cacheAction":"storing","requestedTasks":":old","totalProblemCount":0}""")
            setLastModified(1_000L)
        }
        val newer = File(newerDir, "configuration-cache-report.html").apply {
            writeText("""{"diagnostics":[],"cacheAction":"reused","requestedTasks":":new","totalProblemCount":0}""")
            setLastModified(System.currentTimeMillis())
        }

        val found = ConfigurationCacheReportParser.findNewestReport(root, createdAfterMs = newer.lastModified() - 500)
        assertEquals(newer.absolutePath, found?.absolutePath)
        assertTrue(older.exists())
    }

    @Test
    fun testExtractReportPathFromOutput() {
        val output = """
            Calculating task graph as no cached configuration is available for tasks: :help
            See the complete report at file:///tmp/demo/configuration-cache-report.html
            BUILD SUCCESSFUL
        """.trimIndent()
        assertEquals(
            "/tmp/demo/configuration-cache-report.html",
            ConfigurationCacheReportParser.extractReportPathFromOutput(output)
        )
    }

    private fun unusedPool(): GradleConnectionPool = object : GradleConnectionPool {
        override fun <T> withConnection(projectDir: File, action: (org.gradle.tooling.ProjectConnection) -> T): T {
            error("Not needed")
        }
    }
}
