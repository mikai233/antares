package com.mikai233.common.config.luban.validation

import io.github.realmlabs.asteria.config.ConfigValidator
import io.github.realmlabs.asteria.contribution.AsteriaContributionCatalog

@AsteriaContributionCatalog(
    contract = ConfigValidator::class,
    packageName = "com.mikai233.common.config.luban.validation",
    className = "GeneratedGameConfigValidatorContributions",
)
object GameConfigValidators {
    val defaultValidators: List<ConfigValidator> = GeneratedGameConfigValidatorContributions.ALL
}
