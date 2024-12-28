package com.mikai233.common.db

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.google.common.hash.Hashing
import com.mikai233.common.core.actor.TrackingCoroutineScope
import com.mikai233.common.entity.*
import com.mikai233.common.extension.Json
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.unixTimestamp
import com.mikai233.common.serde.DEPS_EXTRA
import com.mikai233.common.serde.KryoPool
import com.mongodb.*
import com.mongodb.client.MongoClients
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


const val FULL_HASH_THRESHOLD = 100

/**
 * @param kryoPool 用于对象深拷贝的对象池，数据写入在IO线程中操作，需要将数据拷贝一份进行操作
 * @param coroutine 用于执行IO操作的协程
 * @param mongoTemplate 用于执行数据库操作的模板
 * @param period 数据标脏时间分摊，每个字段的标脏操作均匀的分摊到一个时间段内，保证这个时间周期内完成一次所有字段的标脏操作
 * @param tick 外部调用[trace]的时间间隔，用于计算标脏操作的时间分布，例如当前追踪的[entityClass]内部有10个字段，[period]为100s，
 * [tick]为1s，那么每执行10个[trace]，才会进行一个字段的标脏操作，一个[period]完成一次所有字段的标脏操作；如果[tick]为10s，那么每次
 * 执行[trace]就会做一次字段的标脏操操作。
 */
class Tracer<K, E>(
    private val entityClass: KClass<E>,
    private val kryoPool: KryoPool,
    private val coroutine: TrackingCoroutineScope,
    private val period: Duration,
    private val tick: Duration,
    private val mongoTemplate: () -> MongoTemplate,
) where K : Any, E : Entity {
    private val logger = logger()
    private val idField = idField()
    private val entities: MutableMap<K, E> = mutableMapOf()

    private val normalFields: List<KProperty1<E, *>> = normalFields()

    //<id, <field, value> >
    private val normalValues: MutableMap<K, MutableMap<String, Record>> = mutableMapOf()

    private val mapFields: List<KProperty1<E, Map<*, *>>> = mapFields()

    //<id, <field, <key, value> > >
    private val mapValues: MutableMap<K, MutableMap<String, MutableMap<Any?, Record>>> = mutableMapOf()

    //当前标脏到哪个字段了
    private var nextFieldIndex = 0

    //一个完整标脏周期的开始时间
    private var periodStartInstant: Instant = Clock.System.now()

    private val hashFunction = Hashing.goodFastHash(128)
    private var updateJob: Job? = null
    private var flushing = false

    init {
        check(period > tick) { "period:${period} should be greater than tick:${tick}" }
        check(normalFields.size + mapFields.size > 0) { "entity class:${entityClass.qualifiedName} has no field" }
        logger.info(
            "tracer[{}] trace each field duration {}",
            entityClass,
            period / (normalFields.size + mapFields.size)
        )
    }

    private fun fullHashCode(obj: Any?) = hashFunction.hashBytes(Json.toBytes(obj))

    fun trace(currentEntities: Map<K, E>) {
        check(!flushing) { "tracer ${entityClass::class.qualifiedName} is flushing" }
        val now = Clock.System.now()
        val totalFields = normalFields.size + mapFields.size
        val durationPerField = period / totalFields
        val fieldsIndexElapsed = ((now - periodStartInstant) / durationPerField).toInt()
        var n = fieldsIndexElapsed - nextFieldIndex
        //如果n大于总字段数，那么只需执行一次覆盖类中所有字段的标脏操作就行了，没有必要重复执行
        if (n >= totalFields) {
            n = totalFields
        }
        repeat(n) {
            trace0(currentEntities)
            nextFieldIndex++
            if (nextFieldIndex >= normalFields.size + mapFields.size - 1) {
                nextFieldIndex = 0
                periodStartInstant = Clock.System.now()
            }
        }
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

    /**
     * 对[Entity]中的直接非Map类型的字段进行标脏操作
     * 通过反射获取字段的值，计算hash值，如果hash值发生变化，则标记为[Status.Set]
     */
    private fun traceNormalFields(id: K, entity: E) {
        val valueByFieldName = normalValues.computeIfAbsent(id) { mutableMapOf() }
        if (flushing) {
            normalFields.forEach { property ->
                traceNormalField(property, entity, valueByFieldName)
            }
        } else {
            normalFields.getOrNull(nextFieldIndex)?.let { property ->
                traceNormalField(property, entity, valueByFieldName)
            }
        }
    }

    private fun traceNormalField(
        property: KProperty1<E, *>,
        entity: E,
        valueByFieldName: MutableMap<String, Record>
    ) {
        val name = property.name
        val currentValue = property.get(entity)
        logger.trace("trace field:{}, value:{}", name, currentValue)
        val record = valueByFieldName.computeIfAbsent(name) { Record.default() }
        hash(name, currentValue, record)
    }

    /**
     * 对[Entity]中的Map类型的字段进行标脏操作
     * 通过反射获取字段的值，然后迭代这个Map，计算Map中每个Value的hash值，如果hash值发生变化，则标记为[Status.Set]
     * 同时和之前的[mapValues]进行比对，如果之前的Map中有的Key在当前Map中不存在，则标记为[Status.Unset]
     */
    private fun traceMapFields(id: K, entity: E) {
        val valueByFieldName = mapValues.computeIfAbsent(id) { mutableMapOf() }
        if (flushing) {
            mapFields.forEach { property ->
                traceMapField(property, entity, valueByFieldName)
            }
        } else {
            mapFields.getOrNull(nextFieldIndex - normalFields.size)?.let { property ->
                traceMapField(property, entity, valueByFieldName)
            }
        }
    }

    private fun traceMapField(
        property: KProperty1<E, Map<*, *>>,
        entity: E,
        valueByFieldName: MutableMap<String, MutableMap<Any?, Record>>
    ) {
        val name = property.name
        val currentMap = property.get(entity)
        logger.trace("trace map field:{}, value:{}", name, currentMap)
        val valueByMapKey = valueByFieldName.computeIfAbsent(name) { mutableMapOf() }
        //TODO replace . to _ in k
        valueByMapKey.forEach { (k, v) ->
            if (k !in currentMap) {
                v.status = Status.Unset
            }
        }
        currentMap.forEach { (k, v) ->
            val record = valueByMapKey.computeIfAbsent(k) { Record.default() }
            hash("$name.$k", v, record)
        }
    }

    /**
     * 计算哈希值，如果哈希值发生变化，将计算哈希的对象存入[Record.value]中，用于写库，并标记为[Status.Set]
     * 计算哈希时，首先会计算普通哈希值，如果这个对象连续多次计算普通哈希都相同，那么会计算一次复杂哈希
     * 复杂哈希是通过将这个对象进行序列化之后计算的
     */
    private fun hash(name: String, obj: Any?, record: Record) {
        if (record.status.isDirty()) {
            //虽然这个字段已经被标记为脏，但是后续也可能会继续改动此值，需要保持脏数据为最新值
            record.value = obj
            logger.trace("field:{} is dirty, skip", name)
            return
        }
        val preHashCode = record.hashCode
        record.hashCode = obj.hashCode()
        if (preHashCode != record.hashCode) {
            record.hashSameCount = 0
            record.status = Status.Set
            record.value = obj
        } else {
            record.hashSameCount++
        }
        if (record.status.isDirty()) {
            logger.trace("field:{} is dirty", name)
            return
        }
        if (record.hashSameCount >= FULL_HASH_THRESHOLD || flushing) {
            val preFullHashCode = record.fullHashCode
            record.fullHashCode = fullHashCode(obj)
            if (preFullHashCode != record.fullHashCode) {
                record.status = Status.Set
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
        value.status = Status.Clean
        value.value = null
        logger.trace("field:{} is clean", name)
    }

    /**
     * 在将数据从库中加载到内存后，调用此方法，将全部数据标记为干净数据避免下一次标脏产生大量假的脏数据
     */
    fun cleanupAll(entities: Map<K, E>) {
        this.entities.putAll(entities)
        entities.forEach { (id, entity) ->
            normalFields.forEach { property ->
                val name = property.name
                val valueByFieldName = normalValues.computeIfAbsent(id) { mutableMapOf() }
                val record = valueByFieldName.computeIfAbsent(name) { Record.default() }
                cleanup(name, property.get(entity), record)
            }
            mapFields.forEach { property ->
                val name = property.name
                val valueByFieldName = mapValues.computeIfAbsent(id) { mutableMapOf() }
                val valueByMapKey = valueByFieldName.computeIfAbsent(name) { mutableMapOf() }
                val currentMap = property.get(entity)
                valueByMapKey.forEach { (k, v) ->
                    if (k !in currentMap) {
                        v.status = Status.Unset
                    }
                }
                currentMap.forEach { (k, v) ->
                    val record = valueByMapKey.computeIfAbsent(k) { Record.default() }
                    cleanup("$name.$k", v, record)
                }
            }
        }
    }

    private fun updateEntities() {
        if (updateJob?.isActive == true) {
            return
        }
        val template = mongoTemplate()
        val updateOpList = mutableListOf<UpdateOp>()
        var anyValueDirty = false
        normalValues.forEach { (id, valueByFieldName) ->
            valueByFieldName.forEach { (fieldName, record) ->
                if (record.status.isDirty()) {
                    anyValueDirty = true
                    val value = record.value
                    val criteria = Criteria.where(idField.name).`is`(id)
                    val update = Update.update(fieldName, deepCopy(value))
                    updateOpList.add(UpdateOp(Query.query(criteria), update, record))
                    cleanup(fieldName, value, record)
                }
            }
        }
        //map中已删除的字段在执行unset失败时，进行回滚的function
        val rollbackFunction: MutableMap<Int, () -> Unit> = mutableMapOf()
        mapValues.forEach { (id, valueByFieldName) ->
            valueByFieldName.forEach { (fieldName, valueByMapKey) ->
                val iter = valueByMapKey.iterator()
                while (iter.hasNext()) {
                    val (k, record) = iter.next()
                    when (record.status) {
                        Status.Clean -> Unit
                        Status.Set -> {
                            anyValueDirty = true
                            val value = record.value
                            val criteria = Criteria.where(idField.name).`is`(id)
                            val update = Update.update("$fieldName.$k", deepCopy(value))
                            updateOpList.add(UpdateOp(Query.query(criteria), update, record))
                            //TODO: 移除k中的特殊值
                            cleanup("$fieldName.$k", value, record)
                        }

                        Status.Unset -> {
                            anyValueDirty = true
                            val criteria = Criteria.where(idField.name).`is`(id)
                            val update = Update().unset("$fieldName.$k")
                            updateOpList.add(UpdateOp(Query.query(criteria), update, record))
                            iter.remove()
                            rollbackFunction[updateOpList.lastIndex] = {
                                //如果回滚的时候已经有值了，说明是删除后又新增了，保持原来的值不变，不是这种情况才进行回滚
                                valueByMapKey.putIfAbsent(k, record)
                            }
                        }
                    }
                }
            }
        }
        if (!anyValueDirty) {
            return
        }
        updateJob = coroutine.launch {
            val updateResults = updateOpList.map { updateOp ->
                async(Dispatchers.IO) {
                    runCatching {
                        if (updateOp.record.status == Status.Unset) {
                            template.updateFirst(updateOp.query, updateOp.update, entityClass.java)
                        } else {
                            template.upsert(updateOp.query, updateOp.update, entityClass.java)
                        }
                    }
                }
            }.awaitAll()
            //下面的逻辑已经从IO线程回到主线程了，这些操作不会和[trace]操作冲突
            updateResults.forEachIndexed { index, result ->
                if (result.isFailure) {
                    val op = updateOpList[index]
                    logger.error(
                        "update failed, query:${op.query}, update:${op.update}, record:${op.record}",
                        result.exceptionOrNull()
                    )
                    //写入失败，将记录标记为脏数据，下次继续尝试写入
                    when (op.record.status) {
                        Status.Clean -> {
                            //之前的状态是Set 因为做了cleanup 所以是Clean
                            op.record.status = Status.Set
                        }

                        Status.Set -> error("should not happen")
                        Status.Unset -> {
                            //Unset失败了，重新加回去，下次继续尝试删除
                            rollbackFunction[index]?.invoke()
                        }
                    }
                } else {
                    val updateResult = result.getOrThrow()
                    val op = updateOpList[index]
                    logger.debug(
                        "update success, query:{}, update:{}, record:{}, result:{}",
                        op.query,
                        op.update,
                        op.record,
                        updateResult
                    )
                }
            }
        }
    }

    private fun deleteEntities(currentEntities: Map<K, E>) {
        val deletedEntities = entities.filter { it.key !in currentEntities }
        if (deletedEntities.isEmpty()) {
            return
        }
        entities.entries.removeIf { (id, _) -> id !in currentEntities }
        coroutine.launch {
            val template = mongoTemplate()
            val results = deletedEntities.map { (id, entity) ->
                val retryTemplate = retryTemplate()
                async(Dispatchers.IO) {
                    id to runCatching {
                        retryTemplate.execute<_, DataAccessException> {
                            template.remove(entity)
                        }
                    }
                }
            }.awaitAll()
            //下面的逻辑已经从IO线程回到主线程了，这些操作不会和[trace]操作冲突
            results.forEach { (id, result) ->
                if (result.isFailure) {
                    logger.error("delete failed, entity:${entities[id]}", result.exceptionOrNull())
                    //删失败了，重新加回去，下次继续尝试删除
                    val deletedEntity = deletedEntities[id]
                    //entities中又有该id的数据了，说明是删除后又新增了
                    if (entities[id] == null && deletedEntity != null) {
                        entities[id] = deletedEntity
                    }
                } else {
                    val deleteResult = result.getOrThrow()
                    logger.debug("delete success, entity:{}, result:{}", entities[id], deleteResult)
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

    private fun normalFields(): List<KProperty1<E, *>> {
        return entityClass.declaredMemberProperties.filter {
            !it.returnType.jvmErasure.isSubclassOf(Map::class)
        }
    }

    private fun mapFields(): List<KProperty1<E, Map<*, *>>> {
        @Suppress("UNCHECKED_CAST")
        return entityClass.declaredMemberProperties.filter {
            it.returnType.jvmErasure.isSubclassOf(Map::class)
        } as List<KProperty1<E, Map<*, *>>>
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
    val executor = Executor { pendingRunnable.add(it) }
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
    val actorCoroutine = TrackingCoroutineScope(executor.asCoroutineDispatcher())
    val tracer = Tracer<Int, Room>(Room::class, pool, actorCoroutine, 2.minutes, 1.seconds) { template }
    val rooms = mutableMapOf<Int, Room>()
    repeat(3000) {
        val room = Room(
            it + 1,
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
        rooms[room.id] = room
    }
    tracer.cleanupAll(rooms)
    while (true) {
        Thread.sleep(1000)
        tracer.trace(rooms)
        while (pendingRunnable.isNotEmpty()) {
            pendingRunnable.poll().run()
        }
        rooms.values.shuffled().take(100).forEach { randomOp(it) }
    }
}

fun randomOp(room: Room) {
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