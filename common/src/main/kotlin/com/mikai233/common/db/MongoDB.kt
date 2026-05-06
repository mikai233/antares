package com.mikai233.common.db

import com.mikai233.common.config.DataSourceConfig
import com.mikai233.common.config.MongoDeploymentMode
import com.mikai233.common.entity.*
import com.mongodb.*
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.index.Index
import com.mongodb.kotlin.client.coroutine.MongoClient as CoroutineMongoClient
import com.mongodb.reactivestreams.client.MongoClient as ReactiveMongoClient
import com.mongodb.reactivestreams.client.MongoClients as ReactiveMongoClients

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
        val settings = buildSettings()
        coroutineClient = CoroutineMongoClient.create(settings)
        database = coroutineClient.getDatabase(gameDataSourceConfig.databaseName)
        reactiveClient = ReactiveMongoClients.create(settings)
        reactiveTemplate = ReactiveMongoTemplate(
            SimpleReactiveMongoDatabaseFactory(reactiveClient, gameDataSourceConfig.databaseName),
        )
    }

    suspend fun validate() {
        validateConfig()
        val validation = gameDataSourceConfig.validation
        if (!validation.enabled) {
            return
        }
        if (validation.ping) {
            database.runCommand(Document("ping", 1))
        }
        validateRequiredCollections(validation.requiredCollections)
        if (validation.ensureIndexes) {
            ensureIndexes()
        }
    }

    private fun buildSettings(): MongoClientSettings {
        return MongoClientSettings.builder()
            .writeConcern(writeConcern(gameDataSourceConfig.writeConcern))
            .readPreference(readPreference(gameDataSourceConfig.readPreference))
            .applyToClusterSettings { builder ->
                builder.hosts(gameDataSourceConfig.endpoints.map { ServerAddress(it.host, it.port) })
                if (gameDataSourceConfig.mode == MongoDeploymentMode.ReplicaSet) {
                    builder.requiredReplicaSetName(requireNotNull(gameDataSourceConfig.replicaSetName))
                }
            }
            .apply {
                mongoCredential()?.let(::credential)
            }
            .build()
    }

    private fun validateConfig() {
        require(gameDataSourceConfig.databaseName.isNotBlank()) { "Mongo databaseName must not be blank" }
        require(gameDataSourceConfig.endpoints.isNotEmpty()) { "Mongo endpoints must not be empty" }
        require(gameDataSourceConfig.mode != MongoDeploymentMode.ReplicaSet || !gameDataSourceConfig.replicaSetName.isNullOrBlank()) {
            "Mongo replicaSetName is required for ReplicaSet mode"
        }
        require(gameDataSourceConfig.mode != MongoDeploymentMode.ShardedCluster || gameDataSourceConfig.validation.enabled) {
            "Mongo validation must be enabled for ShardedCluster mode"
        }
    }

    private suspend fun validateRequiredCollections(requiredCollections: List<String>) {
        if (requiredCollections.isEmpty()) {
            return
        }
        val existing = database.listCollectionNames().toList().toSet()
        val missing = requiredCollections.filterNot(existing::contains)
        require(missing.isEmpty()) {
            "Mongo database ${gameDataSourceConfig.databaseName} is missing required collections: ${missing.joinToString()}"
        }
    }

    private suspend fun ensureIndexes() {
        ensureAscendingIndex(PlayerActionMongo.COLLECTION, "playerId")
        ensureAscendingIndex(PlayerActivityMongo.COLLECTION, "playerId")
        ensureAscendingIndex(PlayerAbstractMongo.COLLECTION, "worldId")
        ensureAscendingIndex(WorldActionMongo.COLLECTION, "worldId")
        reactiveTemplate.indexOps(ActorConfigSyncStateMongo.COLLECTION)
            .ensureIndex(
                Index()
                    .on("actorKind", Sort.Direction.ASC)
                    .on("actorEntityId", Sort.Direction.ASC),
            )
            .awaitSingle()
    }

    private suspend fun ensureAscendingIndex(collectionName: String, fieldName: String) {
        reactiveTemplate.indexOps(collectionName)
            .ensureIndex(Index().on(fieldName, Sort.Direction.ASC))
            .awaitSingle()
    }

    private fun mongoCredential(): MongoCredential? {
        val username = gameDataSourceConfig.username?.takeIf(String::isNotBlank) ?: return null
        val passwordEnvName = gameDataSourceConfig.passwordEnv?.takeIf(String::isNotBlank)
            ?: error("Mongo passwordEnv is required when username is set")
        val password = System.getenv(passwordEnvName)?.takeIf(String::isNotBlank)
            ?: error("Mongo password environment variable $passwordEnvName is not set")
        return MongoCredential.createCredential(
            username,
            gameDataSourceConfig.authDatabase?.takeIf(String::isNotBlank) ?: gameDataSourceConfig.databaseName,
            password.toCharArray(),
        )
    }
}

private fun writeConcern(value: String): WriteConcern {
    return when (value.trim().lowercase()) {
        "", "default" -> WriteConcern.ACKNOWLEDGED
        "w1", "1" -> WriteConcern.W1
        "majority" -> WriteConcern.MAJORITY
        "unacknowledged" -> WriteConcern.UNACKNOWLEDGED
        else -> error("unsupported Mongo writeConcern=$value")
    }
}

private fun readPreference(value: String?): ReadPreference {
    return when (value?.trim()?.lowercase()) {
        null, "", "primary" -> ReadPreference.primary()
        "primarypreferred", "primary_preferred", "primary-preferred" -> ReadPreference.primaryPreferred()
        "secondary" -> ReadPreference.secondary()
        "secondarypreferred", "secondary_preferred", "secondary-preferred" -> ReadPreference.secondaryPreferred()
        "nearest" -> ReadPreference.nearest()
        else -> error("unsupported Mongo readPreference=$value")
    }
}
