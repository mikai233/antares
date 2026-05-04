package com.mikai233.common.test.db

import com.mikai233.common.entity.PlayerActivity
import com.mikai233.common.entity.PlayerActivityMongo
import com.mikai233.common.entity.WorldAction
import com.mikai233.common.entity.WorldActionMongo
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
import com.mongodb.reactivestreams.client.MongoClients

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
            template.dropCollection(WorldActionMongo.COLLECTION)
                .onErrorResume { reactor.core.publisher.Mono.empty() }
                .awaitSingleOrNull()
            template.dropCollection(PlayerActivityMongo.COLLECTION)
                .onErrorResume { reactor.core.publisher.Mono.empty() }
                .awaitSingleOrNull()
        }
    }

    @Test
    fun completeDocumentShouldReadSuccessfully() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "1_1",
                    "worldId" to 1L,
                    "actionId" to 7,
                    "latestActionMills" to 99L,
                    "actionParam" to 123L,
                )
            ),
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("1_1")),
            WorldAction::class.java,
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("1_1", loaded.id)
        assertEquals(1L, loaded.worldId)
        assertEquals(7, loaded.actionId)
        assertEquals(99L, loaded.latestActionMills)
        assertEquals(123L, loaded.actionParam)
    }

    @Test
    fun legacyDocumentMissingNewFieldsShouldApplyDefaults() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "1_2",
                    "worldId" to 1L,
                    "actionId" to 8,
                )
            ),
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("1_2")),
            WorldAction::class.java,
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("1_2", loaded.id)
        assertEquals(1L, loaded.worldId)
        assertEquals(8, loaded.actionId)
        assertEquals(0L, loaded.latestActionMills)
        assertEquals(0L, loaded.actionParam)
    }

    @Test
    fun legacyDocumentWithNullNewFieldsShouldApplyDefaults() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "1_3",
                    "worldId" to 1L,
                    "actionId" to 9,
                    "latestActionMills" to null,
                    "actionParam" to null,
                )
            ),
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("1_3")),
            WorldAction::class.java,
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("1_3", loaded.id)
        assertEquals(1L, loaded.worldId)
        assertEquals(9, loaded.actionId)
        assertEquals(0L, loaded.latestActionMills)
        assertEquals(0L, loaded.actionParam)
    }

    @Test
    fun documentWithAdditionalUnknownFieldsShouldBeIgnored() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "1_4",
                    "worldId" to 1L,
                    "actionId" to 10,
                    "latestActionMills" to 7L,
                    "actionParam" to 8L,
                    "legacyField" to "legacy",
                    "debugFlag" to true,
                    "nestedExtra" to Document(mapOf("a" to 1, "b" to "x")),
                )
            ),
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("1_4")),
            WorldAction::class.java,
            WorldActionMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("1_4", loaded.id)
        assertEquals(1L, loaded.worldId)
        assertEquals(10, loaded.actionId)
        assertEquals(7L, loaded.latestActionMills)
        assertEquals(8L, loaded.actionParam)
    }

    @Test
    fun playerActivityLegacyDocumentMissingDuplicatedFieldsShouldApplyDefaults() = runBlocking {
        template.insert(
            Document(
                mapOf(
                    "_id" to "7_daily_login",
                    "playerId" to 7L,
                    "activityId" to "daily_login",
                )
            ),
            PlayerActivityMongo.COLLECTION,
        ).awaitSingle()

        val loaded = template.findOne(
            query(where("_id").`is`("7_daily_login")),
            PlayerActivity::class.java,
            PlayerActivityMongo.COLLECTION,
        ).awaitSingle()

        assertEquals("7_daily_login", loaded.id)
        assertEquals(7L, loaded.playerId)
        assertEquals("daily_login", loaded.activityId)
        assertEquals("", loaded.activityName)
        assertEquals(0, loaded.unlockLevel)
        assertEquals("", loaded.conditionSummary)
        assertEquals("", loaded.rewardSummary)
    }
}
