package com.gradle.optimization.mcp.features.runner.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceErrorExtractorTest {
    @Test
    fun testExtractKotlinErrors() {
        val sampleOutput = """
            e: /Users/dev/Project/src/Main.kt: (15, 20): Unresolved reference: foo
            e: file:///Users/dev/Project/src/App.kt: (42, 5): Syntax error
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertEquals(2, errors.size)
        assertEquals("/Users/dev/Project/src/Main.kt", errors[0].file)
        assertEquals(15, errors[0].line)
        assertEquals(20, errors[0].column)
        assertEquals("Unresolved reference: foo", errors[0].message)
        assertEquals("KOTLIN_COMPILER_ERROR", errors[0].errorType)
    }

    @Test
    fun testExtractJavaErrors() {
        val sampleOutput = """
            /Users/dev/Project/src/Helper.java:30: error: cannot find symbol
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertEquals(1, errors.size)
        assertEquals("/Users/dev/Project/src/Helper.java", errors[0].file)
        assertEquals(30, errors[0].line)
        assertEquals("cannot find symbol", errors[0].message)
        assertEquals("JAVA_COMPILER_ERROR", errors[0].errorType)
    }

    @Test
    fun testExtractGradleScriptErrors() {
        val sampleOutput = """
            * What went wrong:
            A problem occurred evaluating root project.
            > Build file '/Users/dev/Project/build.gradle.kts' line: 12
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertTrue(
            errors.any {
                it.errorType == "BUILD_SCRIPT_ERROR" &&
                    it.file == "/Users/dev/Project/build.gradle.kts" &&
                    it.line == 12
            }
        )
    }
}
