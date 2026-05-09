package com.mikai233.common.test.db

import com.mikai233.common.entity.ActorConfigSyncState
import com.mikai233.common.entity.ActorConfigSyncStateMongo
import com.mongodb.reactivestreams.client.MongoClients
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

class SpringDataMongoCompatibilityTest {
    companion object {
        private val mongo = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        private lateinit var template: ReactiveMongoTemplate

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            mongo.start()
            template = ReactiveMongoTemplate(MongoClients.create(mongo.replicaSetUrl), "compatibility_test")
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            mongo.stop()
        }
    }

    @AfterEach
    fun cleanup() {
        runBlocking {
            template.dropCollection(ActorConfigSyncStateMongo.COLLECTION)
                .onErrorResume { reactor.core.publisher.Mono.empty() }
                .awaitSingleOrNull()
        }
    }

    @Test
    fun completeDocumentShouldReadSuccessfully() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "player:1",
                    "actorKind" to "player",
                    "actorEntityId" to "1",
                    "revision" to "r1",
                    "updatedAt" to 99L,
                ),
            ),
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("player:1")),
            ActorConfigSyncState::class.java,
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("player:1", loaded.id)
        assertEquals("player", loaded.actorKind)
        assertEquals("1", loaded.actorEntityId)
        assertEquals("r1", loaded.revision)
        assertEquals(99L, loaded.updatedAt)
    }

    @Test
    fun legacyDocumentMissingNewFieldsShouldApplyDefaults() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "world:1",
                    "actorKind" to "world",
                    "actorEntityId" to "1",
                ),
            ),
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("world:1")),
            ActorConfigSyncState::class.java,
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("world:1", loaded.id)
        assertEquals("world", loaded.actorKind)
        assertEquals("1", loaded.actorEntityId)
        assertEquals("", loaded.revision)
        assertEquals(0L, loaded.updatedAt)
    }

    @Test
    fun legacyDocumentWithNullNewFieldsShouldApplyDefaults() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "world:2",
                    "actorKind" to "world",
                    "actorEntityId" to "2",
                    "revision" to null,
                    "updatedAt" to null,
                ),
            ),
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("world:2")),
            ActorConfigSyncState::class.java,
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("world:2", loaded.id)
        assertEquals("world", loaded.actorKind)
        assertEquals("2", loaded.actorEntityId)
        assertEquals("", loaded.revision)
        assertEquals(0L, loaded.updatedAt)
    }

    @Test
    fun documentWithAdditionalUnknownFieldsShouldBeIgnored() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "player:2",
                    "actorKind" to "player",
                    "actorEntityId" to "2",
                    "revision" to "r2",
                    "updatedAt" to 7L,
                    "legacyField" to "legacy",
                    "debugFlag" to true,
                    "nestedExtra" to Document(mapOf("a" to 1, "b" to "x")),
                ),
            ),
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("player:2")),
            ActorConfigSyncState::class.java,
            ActorConfigSyncStateMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("player:2", loaded.id)
        assertEquals("player", loaded.actorKind)
        assertEquals("2", loaded.actorEntityId)
        assertEquals("r2", loaded.revision)
        assertEquals(7L, loaded.updatedAt)
    }
}
