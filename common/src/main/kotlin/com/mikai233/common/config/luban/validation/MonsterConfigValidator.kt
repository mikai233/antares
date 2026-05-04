package com.mikai233.common.config.luban.validation

import com.mikai233.common.config.luban.GameTables
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.requireComponent

object MonsterConfigValidator : GameConfigValidator {
    override fun validate(snapshot: ConfigSnapshot) {
        val tables = snapshot.requireComponent<GameTables>()
        val itemIds = tables.getTbItem().ids
        val sceneIds = tables.getTbScene().ids

        tables.getTbMonster().all().forEach { monster ->
            check(monster.sceneId in sceneIds) {
                "monster ${monster.id} references missing scene ${monster.sceneId}"
            }
            monster.rewards.forEach { reward ->
                check(reward.itemId in itemIds) {
                    "monster ${monster.id} reward references missing item ${reward.itemId}"
                }
            }
        }
    }
}
