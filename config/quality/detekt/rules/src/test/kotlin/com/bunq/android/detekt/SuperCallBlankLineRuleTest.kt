package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperCallBlankLineRuleTest {
    private val rule = SuperCallBlankLineRule(Config.empty)

    @Test
    fun `reports super call not followed by blank line`() {
        val code = """
            class Foo : Bar() {
                override fun onCreate() {
                    super.onCreate()
                    doWork()
                }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `passes when super call is only statement`() {
        val code = """
            class Foo : Bar() {
                override fun onCreate() {
                    super.onCreate()
                }
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `passes when super call followed by blank line`() {
        val code = """
            class Foo : Bar() {
                override fun onCreate() {
                    super.onCreate()

                    doWork()
                }
            }
        """.trimIndent()
        assertTrue(rule.lint(code).isEmpty())
    }

    @Test
    fun `autocorrects missing blank line after super call`() {
        val autoCorrectRule = SuperCallBlankLineRule(TestConfig("autoCorrect" to true))
        val code = """
            class Foo : Bar() {
                override fun onCreate() {
                    super.onCreate()
                    doWork()
                }
            }
        """.trimIndent()
        val findings = autoCorrectRule.lint(code)
        assertEquals(1, findings.size)
        val fixed = findings.first().entity.ktElement?.containingFile?.text ?: ""
        assertTrue("blank line not inserted", fixed.contains("super.onCreate()\n\n"))
    }
}
