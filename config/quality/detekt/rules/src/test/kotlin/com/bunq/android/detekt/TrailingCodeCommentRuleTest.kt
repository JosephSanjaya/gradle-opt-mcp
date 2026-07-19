package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailingCodeCommentRuleTest {
    private val rule = TrailingCodeCommentRule(Config.empty)

    @Test
    fun `reports trailing comment on code line`() {
        val code = """
            fun foo() {
                val x = 1 // this is a trailing comment
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `reports trailing comment on return statement`() {
        val code = """
            fun foo() {
                if (x) return // early exit
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `passes when comment is on its own line`() {
        val code = """
            fun foo() {
                // standalone comment
                val x = 1
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes with no comments`() {
        val code = """
            fun foo() {
                val x = 1
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes with block comment on its own line`() {
        val code = """
            fun foo() {
                /* block comment */
                val x = 1
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `autocorrects by removing trailing comment and preceding whitespace`() {
        val autoCorrectRule = TrailingCodeCommentRule(TestConfig("autoCorrect" to true))
        val code = """
            fun foo() {
                val x = 1 // trailing comment
            }
        """.trimIndent()
        val findings = autoCorrectRule.lint(code)
        assertEquals(1, findings.size)
        val fixed = findings.first().entity.ktElement?.containingFile?.text ?: ""
        assertTrue("trailing comment not removed", !fixed.contains("// trailing comment"))
        assertTrue("code line preserved", fixed.contains("val x = 1"))
    }
}
