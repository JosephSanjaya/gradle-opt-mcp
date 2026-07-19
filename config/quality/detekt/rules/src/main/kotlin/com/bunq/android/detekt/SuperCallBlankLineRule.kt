package com.bunq.android.detekt

import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperExpression

class SuperCallBlankLineRule(config: Config) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "A super.*() call followed by more statements must be separated by a blank line.",
        Debt.FIVE_MINS,
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val body = function.bodyBlockExpression ?: return
        val statements = body.statements
        if (statements.size < 2) {
            return
        }

        statements.forEachIndexed { index, statement ->
            val isSuperCall = (statement as? KtDotQualifiedExpression)
                ?.receiverExpression is KtSuperExpression
            if (!isSuperCall) {
                return@forEachIndexed
            }

            val nextStatement = statements.getOrNull(index + 1) ?: return@forEachIndexed
            val textBetween = function.containingFile.text
                .substring(statement.textRange.endOffset, nextStatement.textRange.startOffset)
            val hasBlankLine = textBetween.count { it == '\n' } >= 2

            if (!hasBlankLine) {
                report(CodeSmell(issue, Entity.from(statement), "Missing blank line after super call."))
                withAutoCorrect {
                    val whiteSpace = statement.nextSibling as? PsiWhiteSpace ?: return@withAutoCorrect
                    val indent = whiteSpace.text.substringAfterLast('\n')
                    whiteSpace.replace(KtPsiFactory(statement.project).createWhiteSpace("\n\n$indent"))
                }
            }
        }
    }
}
