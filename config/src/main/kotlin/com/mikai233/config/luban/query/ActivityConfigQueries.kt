package com.mikai233.config.luban.query

import com.mikai233.config.luban.ActivityRow
import com.mikai233.config.luban.GameConfigTables
import com.mikai233.config.luban.tbActivity
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.contribution.AsteriaContribution

class ActivityConfigQueries(
    val activitiesByUnlockLevel: Map<Int, List<ActivityRow>>,
)

@AsteriaContribution(GameConfigQueryBuilder::class)
object ActivityConfigQueryBuilder : GameConfigQueryBuilder {
    override val name: String = "game-tbactivity-queries"
    override val type = ActivityConfigQueries::class
    override val dependencies: Set<ConfigTableName> = setOf(GameConfigTables.TbActivity.name)

    override suspend fun build(snapshot: ConfigSnapshot): ActivityConfigQueries {
        return ActivityConfigQueries(
            activitiesByUnlockLevel = snapshot.tbActivity.all().groupBy { it.unlockLevel },
        )
    }
}
