package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigSnapshot
class GameConfigQueries(
    val itemsByType: Map<Int, List<ItemRow>>,
    val monstersBySceneId: Map<Int, List<MonsterRow>>,
    val activitiesByUnlockLevel: Map<Int, List<ActivityRow>>,
    val dropEntriesByItemId: Map<Int, List<com.mikai233.common.config.luban.gen.game.DropEntry>>,
) {
    companion object {
        fun from(snapshot: ConfigSnapshot): GameConfigQueries {
            return GameConfigQueries(
                itemsByType = snapshot.tbItem.all().groupBy { it.type },
                monstersBySceneId = snapshot.tbMonster.all().groupBy { it.sceneId },
                activitiesByUnlockLevel = snapshot.tbActivity.all().groupBy { it.unlockLevel },
                dropEntriesByItemId = snapshot.tbDroppool
                    .all()
                    .flatMap { it.entries }
                    .groupBy { it.itemId },
            )
        }
    }
}

fun interface GameConfigDerivedComponentBuilder {
    fun build(snapshot: ConfigSnapshot): Any
}

object GameConfigDerivedComponents {
    val defaultBuilders: List<GameConfigDerivedComponentBuilder> = listOf(
        GameConfigDerivedComponentBuilder { snapshot -> GameConfigQueries.from(snapshot) },
    )
}
