package com.mikai233.config.luban.validation

import com.mikai233.config.luban.GameConfigTables
import com.mikai233.config.luban.tbDroppool
import com.mikai233.config.luban.tbItem
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigValidationResult
import io.github.realmlabs.asteria.config.ConfigValidator
import io.github.realmlabs.asteria.config.configValidator
import io.github.realmlabs.asteria.contribution.AsteriaContribution

@AsteriaContribution(ConfigValidator::class)
object DropPoolConfigValidator : ConfigValidator {
    private val validator = configValidator { snapshot ->
        val itemIds = snapshot.tbItem.keys

        snapshot.tbDroppool.all().forEach { dropPool ->
            dropPool.entries.forEach { entry ->
                check(
                    condition = entry.itemId in itemIds,
                    message = "drop pool ${dropPool.id} entry references missing item ${entry.itemId}",
                    table = GameConfigTables.TbDroppool.name,
                    id = dropPool.id,
                )
            }
        }
    }

    override suspend fun validate(snapshot: ConfigSnapshot): ConfigValidationResult {
        return validator.validate(snapshot)
    }
}
