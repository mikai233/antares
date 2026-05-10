package com.mikai233.common.config.luban.query

import com.mikai233.common.config.luban.GameConfigTables
import com.mikai233.common.config.luban.ItemRow
import com.mikai233.common.config.luban.tbItem
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.contribution.AsteriaContribution

class ItemConfigQueries(
    val itemsByType: Map<Int, List<ItemRow>>,
)

@AsteriaContribution(GameConfigQueryBuilder::class)
object ItemConfigQueryBuilder : GameConfigQueryBuilder {
    override val name: String = "game-tbitem-queries"
    override val type = ItemConfigQueries::class
    override val dependencies: Set<ConfigTableName> = setOf(GameConfigTables.TbItem.name)

    override suspend fun build(snapshot: ConfigSnapshot): ItemConfigQueries {
        return ItemConfigQueries(
            itemsByType = snapshot.tbItem.all().groupBy { it.type },
        )
    }
}
