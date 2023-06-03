package com.mikai233.common.db

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.entity.*
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.unixTimestamp
import com.mongodb.client.MongoClients
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.agrona.DeadlineTimerWheel
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TrackableMemCacheDB(private val template: MongoTemplate, private val fullHashThreshold: Int = 100) {
    companion object {
        val allowedBuiltinType = setOf(
            typeOf<Byte>(),
            typeOf<UByte>(),
            typeOf<Short>(),
            typeOf<UShort>(),
            typeOf<Int>(),
            typeOf<UInt>(),
            typeOf<Long>(),
            typeOf<ULong>(),
            typeOf<ByteArray>(),
            typeOf<String>(),
            typeOf<List<*>>(),
            typeOf<Set<*>>(),
            typeOf<Boolean>(),
        )
    }

    private val logger = logger()
    private val clock = Clock.System
    private val pendingData: MutableMap<TKey, Pair<PersistentDocument?, PersistentDocument?>> = mutableMapOf()
    internal val traceMap: MutableMap<TKey, TData> = mutableMapOf()
    private val propertiesMap: MutableMap<TKey, Pair<KProperty1<out TraceableFieldEntity<*>, *>, TraceableFieldEntity<*>>> =
        mutableMapOf()
    private val timers: BiMap<Long, TKey> = HashBiMap.create()
    private val hashFunction = Hashing.goodFastHash(128)
    private val timerWheel = DeadlineTimerWheel(TimeUnit.MILLISECONDS, unixTimestamp(), 16, 256)
    private var stopped = true

    internal fun serdeHash(obj: Any?) = hashFunction.hashBytes(Json.toJsonBytes(obj))

    fun traceEntity(entity: Entity<*>) {
        check(stopped) { "trace db is not working, this means you already stopped the trace db" }
        when (entity) {
            is TraceableFieldEntity -> {
                traceEntityByFields(entity)
            }

            is TraceableRootEntity -> {
                traceEntityByRoot(entity)
            }
        }
    }

    private fun traceEntityByFields(entity: TraceableFieldEntity<*>) {
        val entityClazz = entity::class
        checkNotNull(entityClazz.qualifiedName) { "class qualified name is null" }
        val query = Query.query(Criteria.where("_id").`is`(entity.key()))
        entityClazz.declaredMemberProperties.forEach { kp ->
            val returnType = kp.returnType
            val fieldValue = kp.call(entity)
            when {
                returnType.isSubtypeOf(typeOf<Map<*, *>>()) -> {
                    check(kp !is KMutableProperty<*>) { "map field is not allowed mutable in trace entity by fields mode" }
                    check(!returnType.isMarkedNullable) { "map field return type is not allowed nullable in trace entity by fields mode" }
                    val mapKey = TKey(entityClazz, query, kp.name, TType.Map)
                    val mapField = fieldValue as Map<*, *>
                    traceMap[mapKey] = TData(mapField, 0, serdeHash(mapField), 0).also {
                        propertiesMap[mapKey] = kp to entity
                        scheduleTraceCheck(mapKey, it)
                    }
                    mapField.forEach { (k, v) ->
                        checkNotNull(k) { "map key of:${kp.name} in $entityClazz is not allowed nullable" }
                        checkNotNull(v) { "map value of:${kp.name} in $entityClazz is not allowed nullable" }
                        val valueType = v::class.createType()
                        val type = when {
                            isBuiltin(valueType) -> {
                                TType.Builtin
                            }

                            isDataClass(valueType) -> {
                                TType.Data
                            }

                            else -> error("unsupported class:${v::class}")
                        }
                        val mapValueKey = mapKey.copy(path = "${mapKey.path}.$k", type = type)
                        traceMap[mapValueKey] = TData(v, 0, serdeHash(v), 0)
                    }
                }

                isBuiltin(returnType) -> {
                    val fieldKey = TKey(entityClazz, query, kp.name, TType.Builtin)
                    traceMap[fieldKey] = TData(fieldValue, 0, serdeHash(fieldValue), 0).also {
                        propertiesMap[fieldKey] = kp to entity
                        scheduleTraceCheck(fieldKey, it)
                    }
                }

                isDataClass(returnType) -> {
                    val fieldKey = TKey(entityClazz, query, kp.name, TType.Data)
                    traceMap[fieldKey] = TData(fieldValue, 0, serdeHash(fieldValue), 0).also {
                        propertiesMap[fieldKey] = kp to entity
                        scheduleTraceCheck(fieldKey, it)
                    }
                }

                else -> error("unsupported class:${returnType.jvmErasure}")
            }
        }
        logger.trace("{}", traceMap)
        logger.debug("trace entity:{} by fields", entityClazz)
    }

    private fun scheduleTraceCheck(key: TKey, data: TData) {
        val checkDelay = nextCheckTime(data)
        val timerId = timerWheel.scheduleTimer(checkDelay.toEpochMilliseconds())
        logger.trace(
            "schedule check:{} at:{} with timer:{}",
            key,
            checkDelay.toLocalDateTime(GlobalData.zoneId),
            timerId
        )
        timers[timerId] = key
    }

    private fun traceEntityByRoot(entity: TraceableRootEntity<*>) {
        val entityClazz = entity::class
        val query = Query.query(Criteria.where("_id").`is`(entity.key()))
        val key = TKey(entityClazz, query, null, TType.Data)
        val traceData = TData(
            entity,
            entity.hashCode(),
            serdeHash(entity),
            0
        )
        scheduleTraceCheck(key, traceData)
        logger.info("{}", traceData)
        traceMap[key] = traceData
        logger.debug("trace entity:{} by root", entityClazz)
    }

    /**
     * @return true if hash changed
     */
    private fun calHashCode(traceData: TData): Boolean {
        val preHashCode = traceData.objHash
        traceData.objHash = traceData.inner.hashCode()
        return preHashCode != traceData.objHash
    }

    /**
     * @return true if hash changed
     */
    private fun calFullHashCode(traceData: TData): Boolean {
        val preHashCode = traceData.serdeHash
        traceData.serdeHash = hashFunction.hashBytes(Json.toJsonBytes(traceData.inner))
        return preHashCode != traceData.serdeHash
    }

    private fun nextCheckTime(traceData: TData): Instant {
        val delay = 1.minutes - traceData.sameObjHashCount.seconds + (Random.nextInt(10..20).seconds)
        return clock.now().plus(delay)
    }

    fun tick(now: Instant) {
        timerWheel.poll(now.toEpochMilliseconds(), ::handleTimeout, 1000)
    }

    private fun handleTimeout(@Suppress("UNUSED_PARAMETER") timeUnit: TimeUnit, now: Long, timerId: Long): Boolean {
        val key = timers.remove(timerId)
        if (key != null) {
            logger.trace(
                "{} timerId:{} timeout at:{}",
                key,
                timerId,
                Instant.fromEpochMilliseconds(now).toLocalDateTime(GlobalData.zoneId)
            )
            val data = requireNotNull(traceMap[key]) { "track data of key:$key not found" }
            checkDataChangeForKey(key, data)
            scheduleTraceCheck(key, data)
        } else {
            logger.warn("track key of timerId:{} not found", timerId)
        }
        return true
    }

    private fun checkDataChangeForKey(key: TKey, data: TData) {
        when (key.type) {
            TType.Map -> {
                val (delete, add, update) = checkMapValueHash(key, data)
                delete.forEach { (k, v) ->
                    //there is no need to cancel the timer, because each map value not start a timer
                    checkNotNull(traceMap.remove(k)) { "key:$k trace data not found" }
                    persistent(Operation.Delete, k, v)
                }
                add.forEach { (k, v) ->
                    val mapValueData = TData(v, v.hashCode(), serdeHash(v), 0)
                    check(this.traceMap.containsKey(k).not()) { "logic error, trace map contains key:$k" }
                    traceMap[k] = mapValueData
                    persistent(Operation.Save, k, mapValueData)
                }
                update.forEach { (k, v) ->
                    check(this.traceMap.containsKey(k)) { "logic error, trace map not contains key:$k" }
                    persistent(Operation.Update, k, v)
                }
            }

            TType.Data,
            TType.Builtin -> {
                val (kp, entity) = requireNotNull(propertiesMap[key]) { "kp of key:$key not found" }
                val value = kp.call(entity)
                if (value != data.inner) {
                    data.inner = value
                }
                val operation = checkDataHash(data)
                if (operation != null) {
                    persistent(operation, key, data)
                }
            }
        }
    }

    internal fun checkDataHash(traceData: TData): Operation? {
        if (stopped.not()) {
            //stop trace db, force do full hash if obj hash has no change
            traceData.sameObjHashCount = fullHashThreshold
        }
        if (traceData.inner == null) {
            with(traceData) {
                sameObjHashCount = 0
                objHash = 0
                serdeHash = HashCode.fromInt(0)
            }
            return Operation.Delete
        }
        val hashChanged = calHashCode(traceData)
        if (!hashChanged) {
            traceData.sameObjHashCount++
        } else {
            traceData.sameObjHashCount = 0
            return Operation.Update
        }
        if (traceData.sameObjHashCount >= fullHashThreshold) {
            val fullHashChanged = calFullHashCode(traceData)
            if (fullHashChanged) {
                return Operation.Update
            }
            traceData.sameObjHashCount = 0
        }
        return null
    }

    /**
     * @return first: delete data, second: add data, third: update data
     */
    internal fun checkMapValueHash(
        key: TKey,
        data: TData
    ): Triple<Map<TKey, TData>, Map<TKey, Any>, Map<TKey, TData>> {
        check(key.type == TType.Map)
        val map = data.inner as Map<*, *>
        val allMapValue = mutableMapOf<TKey, Any>()
        map.forEach { (k, v) ->
            checkNotNull(k) { "$key map key is null" }
            checkNotNull(v) { "$key map value is null" }
            val valueType = v::class.createType()
            val type = when {
                isBuiltin(valueType) -> TType.Builtin
                isDataClass(valueType) -> TType.Data
                else -> error("logic error, unexpected class:${valueType.jvmErasure}")
            }
            allMapValue[TKey(key.root, key.query, "${key.path}.$k", type)] = v
        }
        val allTracedKeys = mutableSetOf<TKey>()
        val deleteValues = mutableMapOf<TKey, TData>()
        val updateValues = mutableMapOf<TKey, TData>()
        traceMap.forEach { (k, v) ->
            val valuePath = requireNotNull(k.path) { "map value path is null:$k" }
            val keyPath = requireNotNull(key.path) { "map key path is null:$key" }
            if (k.type != TType.Map && valuePath.startsWith(keyPath)) {
                allTracedKeys.add(k)
                val maybeTraced = allMapValue[k]
                //value reference not equals is allowed, only check value is equals or not
                if (maybeTraced != null && (maybeTraced != v.inner || checkDataHash(v) == Operation.Update)) {
                    updateValues[k] = v
                } else if (maybeTraced == null) {
                    deleteValues[k] = v
                }
            }
        }
        val addValues = allMapValue.filter { it.key !in allTracedKeys }
        return Triple(deleteValues, addValues, updateValues)
    }

    private fun persistent(operation: Operation, tKey: TKey, traceData: TData) {
        merge(operation, tKey, traceData)
        val submittingData = arrayListOf<Pair<TKey, PersistentDocument>>()
        val iter = pendingData.iterator()
        while (iter.hasNext()) {
            val next = iter.next()
            val (submitting, pending) = next.value
            if (submitting == null && pending != null) {
                submittingData.add(next.key to pending)
                next.setValue(pending to null)
            }
        }
        // TODO with IO thread
        submittingData.forEach { (k, v) ->
            try {
                v.block.invoke(template)
                val (_, pending) = requireNotNull(pendingData[k]) { "pending data of key:$k not found" }
                pendingData[k] = null to pending
                logger.info("submitted:{} op:{} to db", k, v.operation)
            } catch (e: Exception) {
                logger.error("", e)
            }
        }
    }

    private fun merge(incomingOperation: Operation, key: TKey, data: TData) {
        val (submitting, pending) = pendingData[key] ?: (null to null)
        if (pending == null) {
            val persistentDoc = genPersistentDoc(incomingOperation, key, data)
            pendingData[key] = submitting to persistentDoc
            logger.trace("put pending key:{} {} data:{}", key, incomingOperation, data.inner)
            return
        }
        val mergedOperation = when (incomingOperation) {
            Operation.Save -> {
                when (pending.operation) {
                    Operation.Save -> {
                        error("illegal new operation state:${incomingOperation}")
                    }

                    Operation.Update -> {
                        error("illegal new operation state:${incomingOperation}")
                    }

                    Operation.Delete -> {
                        Operation.Save
                    }
                }
            }

            Operation.Update -> {
                when (pending.operation) {
                    Operation.Save -> {
                        Operation.Save
                    }

                    Operation.Update -> {
                        Operation.Update
                    }

                    Operation.Delete -> {
                        error("illegal new operation state:${incomingOperation}")
                    }
                }
            }

            Operation.Delete -> {
                when (pending.operation) {
                    Operation.Save -> {
                        Operation.Delete
                    }

                    Operation.Update -> {
                        Operation.Delete
                    }

                    Operation.Delete -> {
                        error("illegal new operation state:${incomingOperation}")
                    }
                }
            }
        }
        val persistentDoc = genPersistentDoc(mergedOperation, key, data)
        pendingData[key] = submitting to persistentDoc
        logger.trace(
            "merge pending key:{} incoming:{} pending:{} final:{} data:{}",
            key,
            pending.operation,
            incomingOperation,
            mergedOperation,
            data.inner
        )
    }

    private fun isBuiltin(type: KType): Boolean {
        return allowedBuiltinType.contains(type) || allowedBuiltinType.any {
            type.withNullability(false).isSubtypeOf(it)
        }
    }

    private fun isDataClass(type: KType): Boolean {
        return type.jvmErasure.let {
            it.isData && it.qualifiedName?.startsWith("com.mikai233") == true
        }
    }

    private fun genPersistentDoc(incomingOperation: Operation, key: TKey, traceData: TData): PersistentDocument {
        return when (incomingOperation) {
            Operation.Save,
            Operation.Update -> {
                val data = checkNotNull(traceData.inner) { "save or update data cannot be null" }
                when (val type = key.type) {
                    TType.Data -> {
                        if (key.path != null) {
                            val document = Document()
                            template.converter.write(data, document)
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.updateFirst(key.query, Update.update(key.path, document), key.root.java)
                            }
                        } else {
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.save(data)
                            }
                        }

                    }

                    TType.Builtin -> {
                        if (key.path != null) {
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.updateFirst(key.query, Update.update(key.path, data), key.root.java)
                            }
                        } else {
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.save(data)
                            }
                        }
                    }

                    TType.Map -> error("logic error, unexpected type:${type}")
                }
            }

            Operation.Delete -> {
                if (key.path != null) {
                    PersistentDocument(incomingOperation) { mongoTemplate ->
                        mongoTemplate.updateFirst(key.query, Update().unset(key.path), key.root.java)
                    }
                } else {
                    PersistentDocument(incomingOperation) { mongoTemplate ->
                        mongoTemplate.remove(key.query, key.root.java)
                    }
                }
            }
        }
    }

    fun stopTrace() {
        if (stopped.not()) {
            stopped = true
            timerWheel.clear()
            timers.clear()
            traceMap.forEach { (key, data) ->
                checkDataChangeForKey(key, data)
            }
        } else {
            logger.warn("trace db already stopped, this invoke has no effect")
        }
    }

    fun isAllPendingDataFlushedToDb(): Boolean {
        return pendingData.values.all { (submitting, pending) -> submitting == null && pending == null }
    }
}

fun main() {
    val client = MongoClients.create()
    val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
    val db = TrackableMemCacheDB(template)
    val room = Room(
        1,
        "mikai",
        unixTimestamp(),
        false,
        "",
        hashMapOf(1 to RoomPlayer(1, 1), 2 to RoomPlayer(2, 2)),
        DirectObj(",", 12, 12, false),
        mutableListOf(),
        TrackChild("hello", "world")
    )
    template.save(room)
    db.traceEntity(room)
    room.players.clear()
    room.players[3] = RoomPlayer(12, 12)
    while (true) {
        Thread.sleep(10)
        db.tick(Clock.System.now())
        room.changeableBoolean = Random.nextBoolean()
        if (Random.nextBoolean()) {
            room.players[Random.nextInt()] = RoomPlayer(Random.nextInt(), Random.nextInt())
        }
        if (Random.nextBoolean()) {
            room.players.keys.randomOrNull()?.let {
                requireNotNull(room.players[it]).level = Random.nextInt()
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
    }
}