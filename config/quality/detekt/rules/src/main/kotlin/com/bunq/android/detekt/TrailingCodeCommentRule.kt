package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class TrailingCodeCommentRule(config: Config) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Trailing comments (code followed by // on same line) are not allowed.",
        Debt.FIVE_MINS,
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        file.accept(TrailingCommentVisitor())
    }

    private inner class TrailingCommentVisitor : KtTreeVisitorVoid() {
        override fun visitElement(element: PsiElement) {
            super.visitElement(element)

            if (element !is PsiComment) return
            if (!element.text.startsWith("//")) return

            val previousSibling = element.prevSibling ?: return
            val isTrailing = previousSibling is PsiWhiteSpace && !previousSibling.text.contains('\n')

            if (isTrailing) {
                report(CodeSmell(issue, Entity.from(element), "Trailing comment not allowed."))
                withAutoCorrect {
                    previousSibling.delete()
                    element.delete()
                }
            }
        }
    }
}
