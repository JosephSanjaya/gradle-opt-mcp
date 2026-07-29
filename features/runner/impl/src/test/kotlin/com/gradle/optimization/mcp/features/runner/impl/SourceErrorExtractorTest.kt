package com.gradle.optimization.mcp.features.runner.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceErrorExtractorTest {
    @Test
    fun testExtractKotlinLegacyErrors() {
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
    fun testExtractKotlinModernErrors() {
        val sampleOutput = """
            e: file:///Users/dev/Project/src/Main.kt:4:26 Return type mismatch: expected 'String', actual 'Int'.
            e: /Users/dev/Project/src/App.kt:12:1 Unresolved reference 'foo'.
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertEquals(2, errors.size)
        assertEquals("/Users/dev/Project/src/Main.kt", errors[0].file)
        assertEquals(4, errors[0].line)
        assertEquals(26, errors[0].column)
        assertTrue(errors[0].message.contains("Return type mismatch"))
        assertEquals("KOTLIN_COMPILER_ERROR", errors[0].errorType)
        assertEquals("/Users/dev/Project/src/App.kt", errors[1].file)
        assertEquals(12, errors[1].line)
        assertEquals(1, errors[1].column)
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
    fun testExtractDetektViolations() {
        val sampleOutput = """
            /Users/dev/Project/src/Main.kt:4:1: Exceeded max line length (120) [MaximumLineLength]
            /Users/dev/Project/src/Main.kt:4:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertEquals(2, errors.size)
        assertTrue(errors.all { it.errorType == "DETEKT_VIOLATION" })
        assertTrue(errors.all { it.file == "/Users/dev/Project/src/Main.kt" && it.line == 4 })
        assertTrue(errors.any { it.message.contains("[MaximumLineLength]") })
        assertTrue(errors.any { it.message.contains("[MaxLineLength]") })
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

    @Test
    fun testExtractTestFailureWithBacktickName() {
        val sampleOutput = """
            ScratchFailingTest > deliberately fails for repro FAILED
                org.opentest4j.AssertionFailedError at ScratchFailingTest.kt:9
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)

        assertTrue(
            errors.any {
                it.errorType == "TEST_FAILURE" &&
                    it.task == "deliberately fails for repro" &&
                    it.message.contains("ScratchFailingTest.deliberately fails for repro")
            }
        )
    }

    @Test
    fun testExtractTaskNotFound() {
        val sampleOutput = """
            * What went wrong:
            Task 'thisTaskDoesNotExist' not found in root project 'demo'.
        """.trimIndent()

        val errors = SourceErrorExtractor.extractErrors(sampleOutput)
        assertTrue(errors.any { it.errorType == "TASK_NOT_FOUND" && it.task == "thisTaskDoesNotExist" })
    }

    @Test
    fun testExtractFailureReason() {
        val sampleOutput = """
            FAILURE: Build failed with an exception.

            * What went wrong:
            Execution failed for task ':compileKotlin'.
            > Compilation error. See log for more details

            * Try:
            > Run with --stacktrace option
        """.trimIndent()

        val reason = SourceErrorExtractor.extractFailureReason(sampleOutput)
        assertTrue(reason != null)
        assertTrue(reason!!.contains("Execution failed for task ':compileKotlin'"))
        assertTrue(!reason.contains("* Try:"))
    }

    @Test
    fun testFailureExcerptAnchorsAroundWhatWentWrong() {
        val lines = listOf(
            "Task :ok",
            "Task :compileKotlin FAILED",
            "",
            "FAILURE: Build failed with an exception.",
            "",
            "* What went wrong:",
            "Compilation error",
            "",
            "BUILD FAILED"
        )

        val excerpt = SourceErrorExtractor.failureExcerpt(lines, maxLines = 20)
        assertTrue(excerpt.any { it.contains("What went wrong") })
        assertTrue(excerpt.any { it.contains("Compilation error") })
    }
}
