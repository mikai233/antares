package com.mikai233.common.core

import com.mikai233.common.core.config.ConfigCache
import com.mikai233.common.core.config.DataSourceConfig
import com.mikai233.common.core.config.DataSourceGame
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.apache.curator.x.async.AsyncCuratorFramework
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

class MongoDB(zookeeper: AsyncCuratorFramework) {

    private val gameDataSourceCache = ConfigCache(zookeeper, DataSourceGame, DataSourceConfig::class) {
        buildClient()
    }

    private val gameDataSourceConfig get() = gameDataSourceCache.config

    lateinit var mongoTemplate: MongoTemplate
        private set

    lateinit var client: MongoClient
        private set

    private fun buildClient() {
        val settings = MongoClientSettings.builder()
            .writeConcern(WriteConcern.W1)
            .applyToClusterSettings { builder ->
                val hosts = gameDataSourceConfig.sources
                    .map { it.host to it.port }
                    .map { (host, port) -> com.mongodb.ServerAddress(host, port) }
                builder.hosts(hosts)
            }
            .build()
        client = MongoClients.create(settings)
        val factory = SimpleMongoClientDatabaseFactory(client, gameDataSourceConfig.databaseName)
        mongoTemplate = MongoTemplate(factory)
    }
}