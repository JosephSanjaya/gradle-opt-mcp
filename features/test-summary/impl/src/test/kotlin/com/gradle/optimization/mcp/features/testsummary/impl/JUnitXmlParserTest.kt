package com.gradle.optimization.mcp.features.testsummary.impl

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JUnitXmlParserTest {
    @Test
    fun testParseXmlFile() {
        val tempDir = createTempDirectory("testsum").toFile()
        try {
            val buildDir = File(tempDir, "features/runner/build/test-results/test").apply { mkdirs() }
            val xmlFile = File(buildDir, "TEST-com.example.SampleTest.xml")
            xmlFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.SampleTest" tests="3" failures="1" errors="0" skipped="1" time="0.5">
                    <testcase name="testOne" classname="com.example.SampleTest" time="0.1"/>
                    <testcase name="testTwo" classname="com.example.SampleTest" time="0.2">
                        <failure message="Assertion failed" type="java.lang.AssertionError">java.lang.AssertionError: Assertion failed
                            at com.example.SampleTest.testTwo(SampleTest.kt:20)
                        </failure>
                    </testcase>
                    <testcase name="testThree" classname="com.example.SampleTest" time="0.0">
                        <skipped/>
                    </testcase>
                </testsuite>
                """.trimIndent()
            )

            val parsed = JUnitXmlParser.parseFile(xmlFile, tempDir)
            assertEquals(1, parsed.size)
            val suite = parsed.first()

            assertEquals("com.example.SampleTest", suite.summary.suiteName)
            assertEquals(":features:runner", suite.summary.modulePath)
            assertEquals(3, suite.summary.totalTests)
            assertEquals(1, suite.summary.passedCount)
            assertEquals(1, suite.summary.failedCount)
            assertEquals(1, suite.summary.skippedCount)
            assertEquals(0.5, suite.summary.durationSeconds)

            val cases = suite.testCases
            assertEquals(3, cases.size)

            val failed = cases.find { it.status == "FAILED" }
            assertNotNull(failed)
            assertEquals("testTwo", failed.testName)
            assertEquals("Assertion failed", failed.failureMessage)
            assertEquals("java.lang.AssertionError", failed.failureType)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractModulePath() {
        val root = File("/tmp/project")
        val rootXml = File("/tmp/project/build/test-results/test/TEST-Foo.xml")
        assertEquals(":", JUnitXmlParser.extractModulePath(rootXml, root))

        val featureXml = File("/tmp/project/features/runner/build/test-results/test/TEST-Bar.xml")
        assertEquals(":features:runner", JUnitXmlParser.extractModulePath(featureXml, root))
    }
}
