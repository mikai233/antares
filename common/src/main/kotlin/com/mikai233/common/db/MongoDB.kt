package com.mikai233.common.db

import com.mikai233.common.config.ConfigCache
import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.DataSourceConfig
import com.mongodb.MongoClientSettings
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.apache.curator.x.async.AsyncCuratorFramework
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

//TODO test db on start
class MongoDB(zookeeper: AsyncCuratorFramework) {

    private val gameDataSourceCache = ConfigCache(zookeeper, DATA_SOURCE_GAME, DataSourceConfig::class) {
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
        val mappingContext = MongoMappingContext()
        val factory = SimpleMongoClientDatabaseFactory(client, gameDataSourceConfig.databaseName)
        val defaultResolver = DefaultDbRefResolver(factory)
        val converter = MappingMongoConverter(defaultResolver, mappingContext)
        converter.setMapKeyDotReplacement("#DOT#")
        converter.afterPropertiesSet()
        mongoTemplate = MongoTemplate(factory, converter)
    }
}
