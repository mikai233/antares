package com.mikai233.common.config.luban.validation

import com.mikai233.common.config.luban.tbItem
import com.mikai233.common.config.luban.tbMonster
import com.mikai233.common.config.luban.tbScene
import io.github.realmlabs.asteria.config.ConfigSnapshot

object MonsterConfigValidator : GameConfigValidator {
    override fun validate(snapshot: ConfigSnapshot) {
        val itemIds = snapshot.tbItem.ids
        val sceneIds = snapshot.tbScene.ids

        snapshot.tbMonster.all().forEach { monster ->
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
