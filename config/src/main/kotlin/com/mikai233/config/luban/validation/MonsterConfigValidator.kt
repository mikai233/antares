package com.mikai233.config.luban.validation

import com.mikai233.config.luban.GameConfigTables
import com.mikai233.config.luban.tbItem
import com.mikai233.config.luban.tbMonster
import com.mikai233.config.luban.tbScene
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigValidationResult
import io.github.realmlabs.asteria.config.ConfigValidator
import io.github.realmlabs.asteria.config.configValidator
import io.github.realmlabs.asteria.contribution.AsteriaContribution

@AsteriaContribution(ConfigValidator::class)
object MonsterConfigValidator : ConfigValidator {
    private val validator = configValidator { snapshot ->
        val itemIds = snapshot.tbItem.keys
        val sceneIds = snapshot.tbScene.keys

        snapshot.tbMonster.all().forEach { monster ->
            check(
                condition = monster.sceneId in sceneIds,
                message = "monster ${monster.id} references missing scene ${monster.sceneId}",
                table = GameConfigTables.TbMonster.name,
                id = monster.id,
            )
            monster.rewards.forEach { reward ->
                check(
                    condition = reward.itemId in itemIds,
                    message = "monster ${monster.id} reward references missing item ${reward.itemId}",
                    table = GameConfigTables.TbMonster.name,
                    id = monster.id,
                )
            }
        }
    }

    override suspend fun validate(snapshot: ConfigSnapshot): ConfigValidationResult {
        return validator.validate(snapshot)
    }
}
