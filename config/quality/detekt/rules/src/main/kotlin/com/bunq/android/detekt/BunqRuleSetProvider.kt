package com.bunq.android.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetId
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class BunqRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = "bunq-rules"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            SuperCallBlankLineRule(config),
            BlankLineBeforeReturnRule(config),
            TrailingCodeCommentRule(config),
        ),
    )
}
