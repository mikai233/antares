package com.mikai233.common.db

import com.mikai233.common.config.DataSourceConfig
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import com.mongodb.kotlin.client.coroutine.MongoClient as CoroutineMongoClient
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient
import com.mongodb.reactivestreams.client.MongoClients as ReactiveMongoClients

//TODO test db on start
class MongoDB(
    private val gameDataSourceConfig: DataSourceConfig,
) {
    lateinit var coroutineClient: CoroutineMongoClient
        private set

    lateinit var database: MongoDatabase
        private set

    lateinit var reactiveClient: ReactiveMongoClient
        private set

    lateinit var reactiveTemplate: ReactiveMongoTemplate
        private set

    init {
        buildClient()
    }

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
        coroutineClient = CoroutineMongoClient.create(settings)
        database = coroutineClient.getDatabase(gameDataSourceConfig.databaseName)
        reactiveClient = ReactiveMongoClients.create(settings)
        reactiveTemplate = ReactiveMongoTemplate(
            SimpleReactiveMongoDatabaseFactory(reactiveClient, gameDataSourceConfig.databaseName),
        )
    }
}
