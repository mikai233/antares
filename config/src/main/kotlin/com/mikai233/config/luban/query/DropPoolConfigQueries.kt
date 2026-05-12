package com.mikai233.config.luban.query

import com.mikai233.config.luban.GameConfigTables
import com.mikai233.config.luban.gen.game.DropEntry
import com.mikai233.config.luban.tbDroppool
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.contribution.AsteriaContribution

class DropPoolConfigQueries(
    val dropEntriesByItemId: Map<Int, List<DropEntry>>,
)

@AsteriaContribution(GameConfigQueryBuilder::class)
object DropPoolConfigQueryBuilder : GameConfigQueryBuilder {
    override val name: String = "game-tbdroppool-queries"
    override val type = DropPoolConfigQueries::class
    override val dependencies: Set<ConfigTableName> = setOf(GameConfigTables.TbDroppool.name)

    override suspend fun build(snapshot: ConfigSnapshot): DropPoolConfigQueries {
        return DropPoolConfigQueries(
            dropEntriesByItemId = snapshot.tbDroppool
                .all()
                .flatMap { it.entries }
                .groupBy { it.itemId },
        )
    }
}
