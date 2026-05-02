package com.mikai233.common.db

import com.mikai233.common.config.DataSourceConfig
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.kotlin.client.coroutine.MongoClient as CoroutineMongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

//TODO test db on start
class MongoDB(
    private val gameDataSourceConfig: DataSourceConfig,
) {
    lateinit var mongoTemplate: MongoTemplate
        private set

    lateinit var client: MongoClient
        private set

    lateinit var coroutineClient: CoroutineMongoClient
        private set

    lateinit var database: MongoDatabase
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
        client = MongoClients.create(settings)
        val conversions = MongoCustomConversions.create {}
        val mappingContext = MongoMappingContext()
        mappingContext.setSimpleTypeHolder(conversions.simpleTypeHolder)
        mappingContext.afterPropertiesSet()
        val factory = SimpleMongoClientDatabaseFactory(client, gameDataSourceConfig.databaseName)
        val defaultResolver = DefaultDbRefResolver(factory)
        val converter = MappingMongoConverter(defaultResolver, mappingContext)
        converter.setCustomConversions(conversions)
        converter.setMapKeyDotReplacement("#DOT#")
        converter.afterPropertiesSet()
        mongoTemplate = MongoTemplate(factory, converter)

        val hosts = gameDataSourceConfig.sources.joinToString(",") { "${it.host}:${it.port}" }
        coroutineClient = CoroutineMongoClient.create("mongodb://$hosts")
        database = coroutineClient.getDatabase(gameDataSourceConfig.databaseName)
    }
}
