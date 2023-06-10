package com.mikai233.common.core.component

import com.mikai233.common.core.component.config.GAME_DATA_SOURCE
import com.mikai233.common.core.component.config.GameDataSource
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.inject.XKoin
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

class MongoHolder(private val koin: XKoin) : KoinComponent by koin, AutoCloseable {
    private val configCenter: ZookeeperConfigCenter by inject()
    private val gameDataSource: GameDataSource = configCenter.getConfigEx<GameDataSource>(GAME_DATA_SOURCE)
    private val client: MongoClient
    private val template: MongoTemplate

    init {
        val connectionString = gameDataSource.sources.joinToString(";") { "mongodb://${it.host}:${it.port}" }
        client = MongoClients.create(connectionString)
        template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
    }

    fun getGameTemplate(): MongoTemplate {
        return template
    }

    override fun close() {
        client.close()
    }
}
