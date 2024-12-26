package com.mikai233.common.db

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.hash.Hashing
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.entity.*
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.serde.DEPS_EXTRA
import com.mikai233.common.serde.KryoPool
import com.mongodb.*
import com.mongodb.client.MongoClients
import kotlinx.coroutines.*
import org.springframework.dao.DataAccessException
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure


const val FULL_HASH_THRESHOLD = 100

class Tracer<K, E>(
    private val entityClass: KClass<E>,
    private val kryoPool: KryoPool,
    private val coroutine: ActorCoroutine,
    private val mongoTemplate: () -> MongoTemplate
) where K : Any, E : Entity {
    private val logger = logger()
    private val idField = idField()
    private val entities: MutableMap<K, E> = mutableMapOf()

    //<field, property>
    private val normalFields: Map<String, KProperty1<E, *>> = normalFields()

    //<id, <field, value> >
    private val normalValues: MutableMap<K, MutableMap<String, Record>> = mutableMapOf()

    //<field, property>
    private val mapFields: Map<String, KProperty1<E, Map<*, *>>> = mapFields()

    //<id, <field, <key, value> > >
    private val mapValues: MutableMap<K, MutableMap<String, MutableMap<Any?, Record>>> = mutableMapOf()

    private val hashFunction = Hashing.goodFastHash(128)
    private var updateJob: Job? = null
    private var flushing = false

    private fun fullHashCode(obj: Any?) = hashFunction.hashBytes(Json.toBytes(obj))

    fun trace(currentEntities: Map<K, E>) {
        check(!flushing) { "tracer ${entityClass::class.qualifiedName} is flushing" }
        trace0(currentEntities)
    }

    private fun trace0(currentEntities: Map<K, E>) {
        entities.putAll(currentEntities)
        currentEntities.forEach { (id, entity) ->
            traceNormalFields(id, entity)
            traceMapFields(id, entity)
        }
        updateEntities()
        deleteEntities(currentEntities)
    }

    private fun traceNormalFields(id: K, entity: E) {
        val valueByFieldName = normalValues.computeIfAbsent(id) { mutableMapOf() }
        normalFields.forEach { (name, property) ->
            val currentValue = property.get(entity)
            logger.trace("trace field:{}, value:{}", name, currentValue)
            val record = valueByFieldName.computeIfAbsent(name) { Record.default() }
            hash(name, currentValue, record)
        }
    }

    private fun traceMapFields(id: K, entity: E) {
        val valueByFieldName = mapValues.computeIfAbsent(id) { mutableMapOf() }
        mapFields.forEach { (name, property) ->
            val currentMap = property.get(entity)
            logger.trace("trace map field:{}, value:{}", name, currentMap)
            val valueByMapKey = valueByFieldName.computeIfAbsent(name) { mutableMapOf() }
            val deletedMapKeys = mutableSetOf<Any?>()
            //TODO replace . to _ in k
            valueByMapKey.keys.forEach { k ->
                if (k !in currentMap) {
                    deletedMapKeys.add(k)
                }
            }
            currentMap.forEach { (k, v) ->
                val record = valueByMapKey.computeIfAbsent(k) { Record.default() }
                hash("$name.$k", v, record)
            }
        }
    }

    private fun hash(name: String, obj: Any?, record: Record) {
        if (record.dirty) {
            //虽然这个字段已经被标记为脏，但是后续也可能会继续改动此值，需要保持脏数据为最新值
            record.value = obj
            logger.trace("field:{} is dirty, skip", name)
            return
        }
        val preHashCode = record.hashCode
        record.hashCode = obj.hashCode()
        if (preHashCode != record.hashCode) {
            record.hashSameCount = 0
            record.dirty = true
            record.value = obj
        } else {
            record.hashSameCount++
        }
        if (record.dirty) {
            logger.trace("field:{} is dirty", name)
            return
        }
        if (record.hashSameCount >= FULL_HASH_THRESHOLD || flushing) {
            val preFullHashCode = record.fullHashCode
            record.fullHashCode = fullHashCode(obj)
            if (preFullHashCode != record.fullHashCode) {
                record.dirty = true
                record.value = obj
                logger.trace("field:{} is dirty", name)
            }
            record.hashSameCount = 0
        }
    }

    /**
     * 重新计算hash值，并把数据标记为干净的
     */
    private fun cleanup(name: String, obj: Any?, value: Record) {
        value.hashSameCount = 0
        value.hashCode = obj.hashCode()
        value.fullHashCode = fullHashCode(obj)
        value.dirty = false
        value.value = null
        logger.trace("field:{} is clean", name)
    }

    private fun updateEntities() {
        if (updateJob?.isActive == true) {
            return
        }
        val template = mongoTemplate()
        val upsertList = mutableListOf<Upsert>()
        var anyValueDirty = false
        normalValues.forEach { (id, valueByFieldName) ->
            valueByFieldName.forEach { (fieldName, record) ->
                val value = record.value
                if (value != null) {
                    anyValueDirty = true
                    val criteria = Criteria.where(idField.name).`is`(id)
                    val update = Update.update(fieldName, deepCopy(value))
                    upsertList.add(Upsert(Query.query(criteria), update, record))
                    cleanup(fieldName, value, record)
                }
            }
        }
        if (!anyValueDirty) {
            return
        }
        updateJob = coroutine.launch {
            val upsertResults = upsertList.map { upsert ->
                async(Dispatchers.IO) {
                    runCatching { template.upsert(upsert.query, upsert.update, entityClass.java) }
                }
            }.awaitAll()
            upsertResults.forEachIndexed { index, result ->
                if (result.isFailure) {
                    val upsert = upsertList[index]
                    logger.error(
                        "upsert failed, query:${upsert.query}, update:${upsert.update}, record:${upsert.record}",
                        result.exceptionOrNull()
                    )
                    //写入失败，将记录标记为脏数据，下次继续尝试写入
                    upsert.record.dirty = true
                }
            }
        }
    }

    private fun deleteEntities(currentEntities: Map<K, E>) {
        val deletedEntities = entities.filter { it.key !in currentEntities }
        entities.entries.removeIf { (id, _) -> id !in currentEntities }
        coroutine.launch {
            val template = mongoTemplate()
            val results = entities.map { (id, entity) ->
                val retryTemplate = retryTemplate()
                async(Dispatchers.IO) {
                    id to runCatching {
                        retryTemplate.execute<_, DataAccessException> {
                            template.remove(entity)
                        }
                    }
                }
            }.awaitAll()
            results.forEach { (id, result) ->
                if (result.isFailure) {
                    logger.error("delete failed, entity:${entities[id]}", result.exceptionOrNull())
                } else {
                    //删失败了，重新加回去，下次继续尝试删除
                    val deletedEntity = deletedEntities[id]
                    //entities中又有该id的数据了，说明是删除后又新增了
                    if (entities[id] == null && deletedEntity != null) {
                        entities[id] = deletedEntity
                    }
                }
            }
        }
    }

    /**
     * 为每个追踪值重新计算fullHashCode，将脏数据全部存库
     * 同时不在接受任何[trace]操作
     * @return 是否所有数据都已经存库
     * TODO: 没有判断删除操作
     */
    fun flush(currentEntities: Map<K, E>): Boolean {
        flushing = true
        trace0(currentEntities)
        return updateJob?.isActive == false
    }

    private fun idField(): KProperty1<E, K> {
        val idProperty =
            entityClass.declaredMemberProperties.find { it.javaField?.isAnnotationPresent(Id::class.java) == true }
        @Suppress("UNCHECKED_CAST")
        return requireNotNull(idProperty) { "entity class:${entityClass.qualifiedName} has no id property" } as KProperty1<E, K>
    }

    private fun normalFields(): Map<String, KProperty1<E, *>> {
        return entityClass.declaredMemberProperties.filter {
            !it.returnType.jvmErasure.isSubclassOf(Map::class)
        }.associateBy { it.name }
    }

    private fun mapFields(): Map<String, KProperty1<E, Map<*, *>>> {
        @Suppress("UNCHECKED_CAST")
        return entityClass.declaredMemberProperties.filter {
            it.returnType.jvmErasure.isSubclassOf(Map::class)
        }.associate { it.name to it as KProperty1<E, Map<*, *>> }
    }

    private fun retryTemplate(): RetryTemplate {
        val retryTemplate = RetryTemplate()

        // 设置重试策略
        val retryableExceptions: MutableMap<Class<out Throwable?>, Boolean> = HashMap()
        retryableExceptions[MongoSocketReadException::class.java] = true
        retryableExceptions[MongoSocketWriteException::class.java] = true
        retryableExceptions[MongoTimeoutException::class.java] = true
        retryableExceptions[MongoSocketOpenException::class.java] = true
        retryableExceptions[MongoConnectionPoolClearedException::class.java] = true
        retryableExceptions[MongoNotPrimaryException::class.java] = true
        retryableExceptions[MongoNodeIsRecoveringException::class.java] = true
        retryableExceptions[MongoWriteConcernException::class.java] = true
        retryableExceptions[MongoWriteException::class.java] = true

        val retryPolicy = SimpleRetryPolicy(3, retryableExceptions)
        retryTemplate.setRetryPolicy(retryPolicy)

        // 设置退避策略
        val backOffPolicy = ExponentialBackOffPolicy()
        backOffPolicy.initialInterval = 500
        backOffPolicy.multiplier = 2.0
        backOffPolicy.maxInterval = 5000
        retryTemplate.setBackOffPolicy(backOffPolicy)

        return retryTemplate
    }

    private fun <T> deepCopy(value: T?): T? where T : Any {
        @Suppress("UNCHECKED_CAST")
        return kryoPool.use {
            val outputStream = ByteArrayOutputStream()
            Output(outputStream).use { writeClassAndObject(it, value) }
            Input(outputStream.toByteArray()).use { readClassAndObject(it) } as T?
        }
    }
}

fun main() {
    val client = MongoClients.create("mongodb://localhost:27117")
    val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
    val r = template.findById(1, Room::class.java)
    val pendingRunnable = LinkedList<Runnable>()
    val executor = Executor {
        pendingRunnable.add(it)
    }
    val pool = KryoPool(
        DEPS_EXTRA + arrayOf(
            Room::class,
            RoomPlayer::class,
            DirectObj::class,
            TrackChild::class,
            Cat::class,
            Bird::class
        )
    )
    val actorCoroutine = ActorCoroutine(CoroutineScope(executor.asCoroutineDispatcher()))
    val db = Tracer<Int, Room>(Room::class, pool, actorCoroutine) { template }
    val room = Room(
        1,
        "mikai",
        unixTimestamp(),
        false,
        "",
        hashMapOf(1 to RoomPlayer(1, 1), 2 to RoomPlayer(2, 2)),
        DirectObj(",", 12, 12, false),
        mutableListOf(),
        TrackChild("hello", "world"),
        mutableListOf(Cat("a", 1), Bird("bb")),
        Cat("asdlfkjalsdk", 1)
    )
//    template.save(room)
//    db.traceEntity(room)
    room.players.clear()
    room.players[3] = RoomPlayer(12, 12)
//    db.delete(room)
    while (true) {
        Thread.sleep(200)
        db.trace(mapOf(1 to room))
        while (pendingRunnable.isNotEmpty()) {
            pendingRunnable.poll().run()
        }
        room.changeableBoolean = Random.nextBoolean()
        if (Random.nextBoolean()) {
            room.players[Random.nextInt()] = RoomPlayer(Random.nextInt(), Random.nextInt())
        }
        if (Random.nextBoolean()) {
            room.players.keys.randomOrNull()?.let {
                val player = requireNotNull(room.players[it])
                if (player is RoomPlayer) {
                    player.level = Random.nextInt()
                }
            }
        }
        if (Random.nextBoolean()) {
            room.players.keys.randomOrNull()?.let {
                room.players.remove(it)
            }
        }
        if (Random.nextBoolean()) {
            room.changeableString = Random.nextLong().toString()
        }
        if (Random.nextBoolean()) {
            room.directObj.c = Random.nextLong()
        }
        if (Random.nextBoolean()) {
            room.listObj = null
        }
        if (Random.nextBoolean()) {
            room.listObj =
                generateSequence(1) { it + 1 }.take(Random.nextInt(1..20)).map { it.toString() }.toMutableList()
        }
        if (Random.nextBoolean()) {
            room.trackChild.b = Random.nextLong().toString()
        }
        if (Random.nextBoolean()) {
            room.animals.randomOrNull()?.let {
                room.animals.remove(it)
            }
        }
        if (Random.nextBoolean()) {
            room.animals.add(Bird(Random.nextLong().toString()))
        }
        if (Random.nextBoolean()) {
            room.animals.add(Cat(Random.nextLong().toString(), Random.nextInt()))
        }
        if (Random.nextBoolean()) {
            room.animals = mutableListOf()
        }
        if (Random.nextBoolean()) {
            room.directInterface = Cat(Random.nextLong().toString(), Random.nextInt())
        }
        if (Random.nextBoolean()) {
            room.directInterface = Bird(Random.nextLong().toString())
        }
    }
}
