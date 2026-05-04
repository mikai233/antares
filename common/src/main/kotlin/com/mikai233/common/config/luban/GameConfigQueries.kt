package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.requireComponent

class GameConfigQueries(
    val itemsByType: Map<Int, List<ItemRow>>,
    val monstersBySceneId: Map<Int, List<MonsterRow>>,
    val activitiesByUnlockLevel: Map<Int, List<ActivityRow>>,
    val dropEntriesByItemId: Map<Int, List<com.mikai233.common.config.luban.gen.game.DropEntry>>,
) {
    companion object {
        fun from(tables: GameTables): GameConfigQueries {
            return GameConfigQueries(
                itemsByType = tables.getTbItem().all().groupBy { it.type },
                monstersBySceneId = tables.getTbMonster().all().groupBy { it.sceneId },
                activitiesByUnlockLevel = tables.getTbActivity().all().groupBy { it.unlockLevel },
                dropEntriesByItemId = tables.getTbDroppool()
                    .all()
                    .flatMap { it.entries }
                    .groupBy { it.itemId },
            )
        }

        fun from(snapshot: ConfigSnapshot): GameConfigQueries {
            return from(snapshot.requireComponent<GameTables>())
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
