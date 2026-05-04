package com.mikai233.player.data

import com.mikai233.common.config.luban.ActivityRow
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.db.MongoDB
import com.mikai233.common.entity.PlayerActivity
import com.mikai233.common.entity.PlayerActivityMongo
import com.mikai233.common.entity.PlayerActivityTracked
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query

class PlayerActivityMem(
    private val playerId: Long,
    private val mongoDbProvider: () -> MongoDB,
) : AsteriaTrackedMemData<PlayerActivity, PlayerActivityTracked>(
    PlayerActivityMongo.COLLECTION,
    { mongoDbProvider().database },
    PlayerActivityMongo::wrap,
) {
    private val activitiesById: MutableMap<String, PlayerActivityTracked> = mutableMapOf()
    private val activitiesByConfigId: MutableMap<String, PlayerActivityTracked> = mutableMapOf()

    override suspend fun load() {
        val activities = mongoDbProvider().reactiveTemplate
            .find(query(where("playerId").`is`(playerId)), PlayerActivity::class.java, PlayerActivityMongo.COLLECTION)
            .collectList()
            .awaitSingle()
        activities.forEach {
            val tracked = attachLoaded(it)
            activitiesById[it.id] = tracked
            activitiesByConfigId[it.activityId] = tracked
        }
    }

    fun entities(): Map<String, PlayerActivityTracked> {
        return activitiesById
    }

    fun syncFromConfigs(playerLevel: Int, activityConfigs: Collection<ActivityRow>) {
        val eligible = activityConfigs
            .filter { it.unlockLevel <= playerLevel }
            .associateBy { it.id }

        val removedIds = activitiesByConfigId.keys - eligible.keys
        removedIds.forEach(::removeActivity)

        eligible.forEach { (activityId, config) ->
            val existing = activitiesByConfigId[activityId]
            if (existing == null) {
                val entity = config.toPlayerActivity(playerId)
                val tracked = createTracked(entity)
                activitiesById[tracked.id] = tracked
                activitiesByConfigId[tracked.activityId] = tracked
            } else {
                existing.activityName = config.name
                existing.unlockLevel = config.unlockLevel
                existing.conditionSummary = config.conditionSummary
                existing.rewardSummary = config.rewardSummary
            }
        }
    }

    private fun removeActivity(activityId: String) {
        activitiesByConfigId.remove(activityId)?.also {
            activitiesById.remove(it.id)
            removeTracked(it.id)
        }
    }

    private fun ActivityRow.toPlayerActivity(playerId: Long): PlayerActivity {
        return PlayerActivity(
            id = "${playerId}_$id",
            playerId = playerId,
            activityId = id,
            activityName = name,
            unlockLevel = unlockLevel,
            conditionSummary = conditionSummary,
            rewardSummary = rewardSummary,
        )
    }
}
