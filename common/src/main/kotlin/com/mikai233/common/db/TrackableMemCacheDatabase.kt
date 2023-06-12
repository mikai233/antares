package com.mikai233.common.db

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.entity.*
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.timestampToLocalDateTime
import com.mikai233.common.ext.unixTimestamp
import com.mongodb.client.MongoClients
import kotlinx.coroutines.*
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
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TrackableMemCacheDatabase(
    private val template: () -> MongoTemplate,
    private val coroutine: ActorCoroutine,
    private val fullHashThreshold: Int = 100,
    private val debugMode: Boolean = false,
) {
    companion object {
        //basic type
        val allowedBuiltinType = setOf(
            typeOf<Byte>(),
            typeOf<UByte>(),
            typeOf<Short>(),
            typeOf<UShort>(),
            typeOf<Int>(),
            typeOf<UInt>(),
            typeOf<Long>(),
            typeOf<ULong>(),
            typeOf<String>(),
            typeOf<Boolean>(),
        )
    }

    data class ConvertWrap(val inner: Any)

    private val logger = logger()
    private val clock = Clock.System
    private val pendingData: MutableMap<TKey, Pair<PersistentDocument?, PersistentDocument?>> = mutableMapOf()
    internal val traceMap: MutableMap<TKey, TData> = mutableMapOf()
    private val propertiesMap: MutableMap<TKey, Pair<KProperty1<out TraceableFieldEntity<*>, *>, TraceableFieldEntity<*>>> =
        mutableMapOf()
    private val mapAssociatedValueType: MutableMap<TKey, TType> = mutableMapOf()
    private val timers: BiMap<Long, TKey> = HashBiMap.create()
    private val hashFunction = Hashing.goodFastHash(128)
    private val timerWheel = DeadlineTimerWheel(TimeUnit.MILLISECONDS, unixTimestamp(), 16, 256)
    private var ioJob: Job? = null
    var stopped = false
        private set

    internal fun serdeHash(obj: Any?) = hashFunction.hashBytes(Json.toJsonBytes(obj))

    fun traceEntity(entity: Entity<*>) {
        check(stopped.not()) { "trace db is not working, this means you already stopped the trace db" }
        when (entity) {
            is TraceableFieldEntity -> {
                traceEntityByFields(entity)
            }

            is TraceableRootEntity -> {
                traceEntityByRoot(entity)
            }

            is ImmutableEntity -> error("this entity is mark at immutable, trace is unused")
        }
    }

    fun saveAndTrace(entity: Entity<*>) {
        val root = entity::class
        val isTraced = traceMap.any { it.key.root == root }
        if (isTraced) {
            logger.warn("entity:{} already traced, cannot be saved and trace", entity)
            return
        }
        coroutine.launch {
            //TODO retry
            val entitySnapshot = Json.deepCopy(entity)
            withContext(Dispatchers.IO) {
                template().save(entitySnapshot)
            }
            traceEntity(entity)
        }
    }

    fun deleteAndCancelTrace(entity: Entity<*>) {
        val root = entity::class
        val iter = traceMap.iterator()
        val removeKeys = mutableSetOf<TKey>()
        while (iter.hasNext()) {
            val next = iter.next()
            val key = next.key
            if (key.root == root) {
                iter.remove()
                removeKeys.add(key)
                if (key.type == TType.Map) {
                    checkNotNull(mapAssociatedValueType.remove(key)) { "logic error, key:$key not found" }
                }
            }
        }
        val inverseTimer = timers.inverse()
        removeKeys.forEach { key ->
            //map value has no timer, may remove none exits timer
            inverseTimer.remove(key)?.let { timerId ->
                check(timerWheel.cancelTimer(timerId)) { "logic error, timerId:$timerId not found" }
            }
        }
        if (removeKeys.isEmpty()) {
            logger.warn("there's no trace entity to delete:{}", entity)
            return
        }
        val query = Query.query(Criteria.where("_id").`is`(entity.key()))
        val key = TKey(root, query, null, TType.Data)
        val data = TData(entity, 0, HashCode.fromInt(0), 0)
        persistent(Operation.Delete, key, data)
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
                    val mapKeyType = checkNotNull(returnType.arguments[0].type) { "$kp map key type is null" }
                    check(mapKeyType.jvmErasure.isAbstract.not()) { "$kp map key cannot be abstract" }
                    val mapValueType = checkNotNull(returnType.arguments[1].type) { "$kp map value type is null" }
                    val type = when {
                        isBuiltin(mapValueType) -> {
                            TType.Builtin
                        }

                        isData(mapValueType) -> {
                            TType.Data
                        }

                        isAbstract(mapValueType) -> {
                            TType.Abstract
                        }

                        else -> error("unsupported class:${mapValueType.jvmErasure}")
                    }
                    val mapKey = TKey(entityClazz, query, kp.name, TType.Map)
                    mapAssociatedValueType[mapKey] = type
                    val mapField = fieldValue as Map<*, *>
                    traceMap[mapKey] = TData(mapField, 0, serdeHash(mapField), 0).also {
                        propertiesMap[mapKey] = kp to entity
                        scheduleTraceCheck(mapKey, it)
                    }
                    mapField.forEach { (k, v) ->
                        checkNotNull(k) { "map key of:${kp.name} in $entityClazz is not allowed nullable" }
                        checkNotNull(v) { "map value of:${kp.name} in $entityClazz is not allowed nullable" }
                        val mapValueKey = mapKey.copy(path = "${mapKey.path}.$k", type = type)
                        traceMap[mapValueKey] = TData(v, 0, serdeHash(v), 0)
                    }
                }

                isBuiltin(returnType) -> {
                    if (kp is KMutableProperty<*>) {
                        val fieldKey = TKey(entityClazz, query, kp.name, TType.Builtin)
                        traceMap[fieldKey] = TData(fieldValue, 0, serdeHash(fieldValue), 0).also {
                            propertiesMap[fieldKey] = kp to entity
                            scheduleTraceCheck(fieldKey, it)
                        }
                    }
                }

                isData(returnType) -> {
                    val fieldKey = TKey(entityClazz, query, kp.name, TType.Data)
                    traceMap[fieldKey] = TData(fieldValue, 0, serdeHash(fieldValue), 0).also {
                        propertiesMap[fieldKey] = kp to entity
                        scheduleTraceCheck(fieldKey, it)
                    }
                }

                isAbstract(returnType) -> {
                    val fieldKey = TKey(entityClazz, query, kp.name, TType.Abstract)
                    traceMap[fieldKey] = TData(fieldValue, 0, serdeHash(fieldValue), 0).also {
                        propertiesMap[fieldKey] = kp to entity
                        scheduleTraceCheck(fieldKey, it)
                    }
                }

                else -> error("unsupported class:${returnType.jvmErasure}")
            }
        }
        logger.debug("trace entity:{} by fields", entityClazz)
    }

    private fun scheduleTraceCheck(key: TKey, data: TData) {
        val checkDelay = nextCheckInstant(data)
        val timerId = timerWheel.scheduleTimer(checkDelay.toEpochMilliseconds())
        if (logger.isTraceEnabled) {
            logger.trace(
                "schedule check:{} at:{} with timer:{}",
                key,
                checkDelay.toLocalDateTime(GlobalData.zoneId),
                timerId
            )
        }
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

    private fun nextCheckInstant(traceData: TData): Instant {
        val delay = if (debugMode) {
            1.seconds
        } else {
            1.minutes - traceData.sameObjHashCount.seconds + (Random.nextInt(10..20).seconds)
        }
        return clock.now().plus(delay)
    }

    fun tick(now: Instant) {
        timerWheel.poll(now.toEpochMilliseconds(), ::handleTimeout, 1000)
    }

    private fun handleTimeout(@Suppress("UNUSED_PARAMETER") timeUnit: TimeUnit, now: Long, timerId: Long): Boolean {
        val key = timers.remove(timerId)
        if (key != null) {
            if (logger.isTraceEnabled) {
                logger.trace(
                    "{} timerId:{} timeout at:{}",
                    key,
                    timerId,
                    timestampToLocalDateTime(now)
                )
            }
            val data =
                requireNotNull(traceMap[key]) { "logic error, trace data of key:$key not found" }
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
                    checkNotNull(traceMap.remove(k)) { "logic error, key:$k trace data not found" }
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
            TType.Abstract,
            TType.Builtin -> {
                if (key.path == null) {
                    //trace by root
                    val operation = checkDataHash(data)
                    if (operation != null) {
                        persistent(operation, key, data)
                    }
                } else {
                    //trace by fields
                    val (kp, entity) = requireNotNull(propertiesMap[key]) { "logic error, kp of key:$key not found" }
                    val currentValue = kp.call(entity)
                    when {
                        currentValue != null && currentValue != data.inner && data.inner != null -> {
                            data.inner = currentValue
                            checkDataHash(data)
                            //update
                            persistent(Operation.Update, key, data)
                        }

                        currentValue != data.inner && data.inner == null -> {
                            data.inner = currentValue
                            checkDataHash(data)
                            //save
                            persistent(Operation.Save, key, data)
                        }

                        else -> {
                            val operation = checkDataHash(data)
                            if (operation != null) {
                                persistent(operation, key, data)
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun checkDataHash(traceData: TData): Operation? {
        if (stopped) {
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
        logger.trace("cal hash for:{} changed:{}", traceData, hashChanged)
        if (!hashChanged) {
            traceData.sameObjHashCount++
        } else {
            traceData.sameObjHashCount = 0
            return Operation.Update
        }
        if (traceData.sameObjHashCount >= fullHashThreshold) {
            val fullHashChanged = calFullHashCode(traceData)
            logger.trace("cal full hash for:{} changed:{}", traceData, fullHashChanged)
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
        val type = requireNotNull(mapAssociatedValueType[key]) { "logic error, $key map value type not found" }
        map.forEach { (k, v) ->
            checkNotNull(k) { "logic error, $key map key is null" }
            checkNotNull(v) { "logic error, $key map value is null" }
            allMapValue[TKey(key.root, key.query, "${key.path}.$k", type)] = v
        }
        val allTracedKeys = mutableSetOf<TKey>()
        val deleteValues = mutableMapOf<TKey, TData>()
        val updateValues = mutableMapOf<TKey, TData>()
        traceMap.forEach { (k, v) ->
            val valuePath = requireNotNull(k.path) { "logic error, map value path is null:$k" }
            val keyPath = requireNotNull(key.path) { "logic error, map key path is null:$key" }
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

    private fun persistent(operation: Operation, key: TKey, data: TData) {
        merge(operation, key, data)
        if (ioJob?.isCompleted == false) {
            return
        }
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
        if (submittingData.isEmpty()) {
            return
        }
        ioJob = coroutine.launch {
            val completedKeys = withContext(Dispatchers.IO) {
                logger.debug("flushing changed data to db:{}", submittingData)
                val completeKeys = arrayListOf<TKey>()
                submittingData.forEach { (k, v) ->
                    try {
                        v.block.invoke(template())
                        completeKeys.add(k)
                        logger.debug("submitted:{} op:{} to db", k, v.operation)
                    } catch (e: Exception) {
                        logger.error("submitting key:${k} op:${v.operation} error", e)
                    }
                }
                completeKeys
            }
            logger.debug("remove flushed data from pendingData:{}", completedKeys)
            completedKeys.forEach { key ->
                val (_, pending) = requireNotNull(pendingData[key]) { "pending data of key:$key not found" }
                pendingData[key] = null to pending
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
                        error("illegal new operation state:${incomingOperation}, pending:${pending.operation}, key:$key, data:$data")
                    }

                    Operation.Update -> {
                        error("illegal new operation state:${incomingOperation}, pending:${pending.operation}, key:$key, data:$data")
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
                        error("illegal new operation state:${incomingOperation}, pending:${pending.operation}, key:$key, data:$data")
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
                        error("illegal new operation state:${incomingOperation}, pending:${pending.operation}, key:$key, data:$data")
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
        return allowedBuiltinType.contains(type)
    }

    private fun isData(type: KType): Boolean {
        return type.jvmErasure.isData && type.jvmErasure.qualifiedName?.startsWith("com.mikai233") == true
    }

    private fun isAbstract(type: KType): Boolean {
        return (type.jvmErasure.isAbstract && type.jvmErasure.qualifiedName?.startsWith("com.mikai233") == true) ||
                isSubTypeOfListAndSet(type.jvmErasure)
    }

    private fun genPersistentDoc(incomingOperation: Operation, key: TKey, data: TData): PersistentDocument {
        return when (incomingOperation) {
            Operation.Save,
            Operation.Update -> {
                val inner = checkNotNull(data.inner) { "logic error, save or update data cannot be null" }
                when (val type = key.type) {
                    TType.Data -> {
                        if (key.path != null) {
                            val document = writeDoc(inner)
                            document.remove("_class")
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.updateFirst(key.query, Update.update(key.path, document), key.root.java)
                            }
                        } else {
                            val document = writeDoc(inner)
                            val collectionName = template().getCollectionName(inner::class.java)
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.save(inner, collectionName)
                            }
                        }
                    }

                    TType.Abstract -> {
                        if (key.path != null) {
                            val copy = if (isSubTypeOfListAndSet(inner::class)) {
                                writeListAndSet(inner)
                            } else {
                                writeDoc(inner)
                            }
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.updateFirst(key.query, Update.update(key.path, copy), key.root.java)
                            }
                        } else {
                            val document = writeDoc(inner)
                            val collectionName = template().getCollectionName(inner::class.java)
                            PersistentDocument(incomingOperation) { mongoTemplate ->
                                mongoTemplate.save(document, collectionName)
                            }
                        }
                    }

                    TType.Builtin -> {
                        val path = requireNotNull(key.path) { "logic error, $key path is null in builtin type" }
                        PersistentDocument(incomingOperation) { mongoTemplate ->
                            mongoTemplate.updateFirst(key.query, Update.update(path, inner), key.root.java)
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
            logger.trace("start force check all traced data change:{}", traceMap)
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

    private fun writeDoc(data: Any): Document {
        val document = Document()
        template().converter.write(data, document)
        return document
    }

    private fun isSubTypeOfListAndSet(clazz: KClass<*>): Boolean {
        return clazz.isSubclassOf(List::class) || clazz.isSubclassOf(Set::class)
    }

    private fun writeListAndSet(data: Any): Any {
        return writeDoc(ConvertWrap(data))[ConvertWrap::inner.name]!!
    }
}

fun main() {
    val client = MongoClients.create()
    val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
    val r = template.findById(1, Room::class.java)
    val pendingRunnable = LinkedList<Runnable>()
    val executor = Executor {
        pendingRunnable.add(it)
    }
    val actorCoroutine = ActorCoroutine(CoroutineScope(executor.asCoroutineDispatcher()))
    val db = TrackableMemCacheDatabase({ template }, actorCoroutine, debugMode = true)
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
    db.saveAndTrace(room)
//    template.save(room)
//    db.traceEntity(room)
    room.players.clear()
    room.players[3] = RoomPlayer(12, 12)
//    db.delete(room)
    while (true) {
        Thread.sleep(10)
        db.tick(Clock.System.now())
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
