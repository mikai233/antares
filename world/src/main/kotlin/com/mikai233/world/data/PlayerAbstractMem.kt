package com.mikai233.world.data

import com.mikai233.common.db.AsteriaTrackedMemData
import com.mikai233.common.db.MongoDB
import com.mikai233.world.entity.PlayerAbstract
import com.mikai233.world.entity.PlayerAbstractMongo
import com.mikai233.world.entity.PlayerAbstractTracked
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query

class PlayerAbstractMem(
    private val worldId: Long,
    private val mongoDbProvider: () -> MongoDB,
) :
    AsteriaTrackedMemData<PlayerAbstract, PlayerAbstractTracked>(
        PlayerAbstractMongo.COLLECTION,
        { mongoDbProvider().database },
        PlayerAbstractMongo::wrap,
    ),
    Map<Long, PlayerAbstractTracked> {
    private val playerAbstracts: MutableMap<Long, PlayerAbstractTracked> = mutableMapOf()
    private val accountToAbstracts: MutableMap<String, PlayerAbstractTracked> = mutableMapOf()

    override suspend fun load() {
        val playerAbstractList = mongoDbProvider().reactiveTemplate
            .find(query(where("worldId").`is`(worldId)), PlayerAbstract::class.java, PlayerAbstractMongo.COLLECTION)
            .collectList()
            .awaitSingle()
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
