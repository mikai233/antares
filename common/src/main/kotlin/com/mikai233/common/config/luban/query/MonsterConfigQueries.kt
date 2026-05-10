package com.mikai233.common.config.luban.query

import com.mikai233.common.config.luban.GameConfigTables
import com.mikai233.common.config.luban.MonsterRow
import com.mikai233.common.config.luban.tbMonster
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.contribution.AsteriaContribution

class MonsterConfigQueries(
    val monstersBySceneId: Map<Int, List<MonsterRow>>,
)

@AsteriaContribution(GameConfigQueryBuilder::class)
object MonsterConfigQueryBuilder : GameConfigQueryBuilder {
    override val name: String = "game-tbmonster-queries"
    override val type = MonsterConfigQueries::class
    override val dependencies: Set<ConfigTableName> = setOf(GameConfigTables.TbMonster.name)

    override suspend fun build(snapshot: ConfigSnapshot): MonsterConfigQueries {
        return MonsterConfigQueries(
            monstersBySceneId = snapshot.tbMonster.all().groupBy { it.sceneId },
        )
    }
}
