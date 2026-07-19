package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankLineBeforeReturnRuleTest {
    private val rule = BlankLineBeforeReturnRule(Config.empty)

    @Test
    fun `reports return not preceded by blank line`() {
        val code = """
            fun compute(): Int {
                val x = calculate()
                return x
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `passes for early-return guard with if single-line`() {
        val code = """
            fun process(input: String?) {
                if (input == null) return
                doWork(input)
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes when return is only statement`() {
        val code = """
            fun getValue(): Int {
                return 42
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes when blank line precedes return`() {
        val code = """
            fun compute(): Int {
                val x = calculate()

                return x
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes for early-return guard with block if`() {
        val code = """
            fun process(input: String?) {
                if (input == null) {
                    return
                }
                doWork(input)
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `autocorrects missing blank line before return`() {
        val autoCorrectRule = BlankLineBeforeReturnRule(TestConfig("autoCorrect" to true))
        val code = """
            fun compute(): Int {
                val x = calculate()
                return x
            }
        """.trimIndent()
        val findings = autoCorrectRule.lint(code)
        assertEquals(1, findings.size)
        val fixed = findings.first().entity.ktElement?.containingFile?.text ?: ""
        assertTrue("blank line not inserted", fixed.contains("calculate()\n\n"))
    }
}
