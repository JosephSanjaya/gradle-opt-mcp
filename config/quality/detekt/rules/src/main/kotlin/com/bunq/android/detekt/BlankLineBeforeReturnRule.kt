package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression

class BlankLineBeforeReturnRule(config: Config) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "A return statement must be preceded by a blank line (except early-return guards).",
        Debt.FIVE_MINS,
    )

    override fun visitReturnExpression(expression: KtReturnExpression) {
        super.visitReturnExpression(expression)

        val block = expression.parent as? KtBlockExpression ?: return
        val statements = block.statements
        val index = statements.indexOf(expression)

        if (index <= 0) {
            return
        }

        val previousStatement = statements[index - 1]
        if (isEarlyReturnGuard(previousStatement)) {
            return
        }

        val textBetween = expression.containingFile.text
            .substring(previousStatement.textRange.endOffset, expression.textRange.startOffset)
        val hasBlankLine = textBetween.count { it == '\n' } >= 2

        if (!hasBlankLine) {
            report(CodeSmell(issue, Entity.from(expression), "Missing blank line before return statement."))
            withAutoCorrect {
                val previousWhiteSpace = expression.prevSibling as? PsiWhiteSpace ?: return@withAutoCorrect
                val indent = previousWhiteSpace.text.substringAfterLast('\n')
                previousWhiteSpace.replace(KtPsiFactory(expression.project).createWhiteSpace("\n\n$indent"))
            }
        }
    }

    private fun isEarlyReturnGuard(
        previousStatement: KtExpression,
    ): Boolean {
        val ifExpression = previousStatement as? KtIfExpression ?: return false
        val thenExpression = ifExpression.then ?: return false
        val elseExpression = ifExpression.`else`
        if (elseExpression != null) {
            return false
        }
        val thenBody = thenExpression as? KtBlockExpression
        val thenStatements = thenBody?.statements ?: listOf(thenExpression)

        return thenStatements.size == 1 && thenStatements.single() is KtReturnExpression
    }
}
