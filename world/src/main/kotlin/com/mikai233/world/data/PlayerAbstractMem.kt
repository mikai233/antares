package com.mikai233.world.data

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.entity.PlayerAbstract
import com.mikai233.common.entity.PlayerAbstractMongo
import com.mikai233.common.entity.PlayerAbstractTracked
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.where

class PlayerAbstractMem(
    private val worldId: Long,
    private val mongoTemplate: () -> MongoTemplate,
    mongoDatabase: () -> MongoDatabase,
) :
    AsteriaTrackedMemData<PlayerAbstract, PlayerAbstractTracked>(
        PlayerAbstractMongo.COLLECTION,
        mongoDatabase,
        PlayerAbstractMongo::wrap,
    ),
    Map<Long, PlayerAbstractTracked> {
    private val playerAbstracts: MutableMap<Long, PlayerAbstractTracked> = mutableMapOf()
    private val accountToAbstracts: MutableMap<String, PlayerAbstractTracked> = mutableMapOf()

    override suspend fun load() {
        val template = mongoTemplate()
        val playerAbstractList =
            template.find<PlayerAbstract>(Query.query(where(PlayerAbstract::worldId).`is`(worldId)))
        playerAbstractList.forEach {
            val tracked = attachLoaded(it)
            playerAbstracts[it.id] = tracked
            accountToAbstracts[it.account] = tracked
        }
    }

    fun entities(): Map<Long, PlayerAbstractTracked> {
        return playerAbstracts
    }

    fun addAbstract(abstract: PlayerAbstract) {
        check(playerAbstracts.containsKey(abstract.id).not()) { "abstract:${abstract.id} already exists" }
        val tracked = createTracked(abstract)
        playerAbstracts[abstract.id] = tracked
        accountToAbstracts[abstract.account] = tracked
    }

    fun delAbstract(playerAbstract: PlayerAbstractTracked) {
        accountToAbstracts.remove(playerAbstract.account)
        playerAbstracts.remove(playerAbstract.id)
        removeTracked(playerAbstract.id)
    }

    fun getByAccount(account: String) = accountToAbstracts[account]

    override val size: Int
        get() = playerAbstracts.size
    override val entries: Set<Map.Entry<Long, PlayerAbstractTracked>>
        get() = playerAbstracts.entries
    override val keys: Set<Long>
        get() = playerAbstracts.keys
    override val values: Collection<PlayerAbstractTracked>
        get() = playerAbstracts.values

    override fun containsKey(key: Long): Boolean {
        return playerAbstracts.containsKey(key)
    }

    override fun containsValue(value: PlayerAbstractTracked): Boolean {
        return playerAbstracts.containsValue(value)
    }

    override fun get(key: Long): PlayerAbstractTracked? {
        return playerAbstracts[key]
    }

    override fun isEmpty(): Boolean {
        return playerAbstracts.isEmpty()
    }
}
