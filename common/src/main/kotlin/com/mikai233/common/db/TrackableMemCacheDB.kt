package com.mikai233.common.db

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.entity.*
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.unixTimestamp
import com.mikai233.common.ext.upperCamelToLowerCamel
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
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds

enum class TraceType {
    NormalField,
    MapField,
    MapValue,
}

data class TraceKey(val path: String, val type: TraceType)

data class TraceData(
    val data: Any?,
    var hashCode: Int,
    var fullHashCode: HashCode,
    var sameHashCount: Int
) {
    companion object {
        fun of(data: Any?, hashFunction: HashFunction): TraceData {
            return TraceData(data, data.hashCode(), hashFunction.hashBytes(Json.toJsonBytes(data)), 0)
        }
    }
}

class TrackableMemCacheDB(private val template: MongoTemplate, private val fullHashThreshold: Int = 100) {
    private val logger = logger()
    private val clock = Clock.System
    private val pendingData: MutableMap<TraceKey, Pair<PersistentDocument?, PersistentDocument?>> = mutableMapOf()
    internal val traceMap: MutableMap<TraceKey, TraceData> = mutableMapOf()
    private val timers: BiMap<Long, TraceKey> = HashBiMap.create()
    internal val hashFunction = Hashing.goodFastHash(128)
    private val timerWheel = DeadlineTimerWheel(TimeUnit.MILLISECONDS, unixTimestamp(), 16, 256)

    fun traceEntity(entity: Entity<*>) {
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
        val root = requireNotNull(entityClazz.simpleName) { "class simple name is null" }.upperCamelToLowerCamel()
        entityClazz.declaredMemberProperties.forEach { kp ->
            val returnType = kp.returnType
            val fieldValue = kp.call(entity)
            when {
                returnType.isSubtypeOf(typeOf<Map<*, *>>()) -> {
                    check(kp !is KMutableProperty<*>) { "map field is not allowed mutable in trace entity by fields mode" }
                    check(!returnType.isMarkedNullable) { "map field return type is not allowed nullable in trace entity by fields mode" }
                    val mapKey = TraceKey("$root.${entity.key()}.${kp.name}", TraceType.MapField)
                    val mapField = fieldValue as Map<*, *>
                    traceMap[mapKey] = TraceData.of(mapField, hashFunction).also {
                        scheduleTraceCheck(mapKey, it)
                    }
                    mapField.forEach { (k, v) ->
                        checkNotNull(k) { "map key of:${kp.name} in $entityClazz is not allowed nullable" }
//                        checkNotNull(v) { "map value of:${kp.name} in $entityClazz is not allowed nullable" }
                        val mapValueKey = TraceKey("${mapKey.path}.$k", TraceType.MapValue)
                        traceMap[mapValueKey] = TraceData.of(v, hashFunction)
                    }
                }

                else -> {
                    val fieldKey = TraceKey("$root.${entity.key()}.${kp.name}", TraceType.NormalField)
                    traceMap[fieldKey] = TraceData.of(fieldValue, hashFunction).also {
                        scheduleTraceCheck(fieldKey, it)
                    }
                }
            }
        }
        logger.trace("{}", traceMap)
        logger.debug("trace entity:{} by fields", entityClazz)
    }

    private fun scheduleTraceCheck(traceKey: TraceKey, traceData: TraceData) {
        val checkDelay = nextCheckTime(traceData)
        val timerId = timerWheel.scheduleTimer(checkDelay.toEpochMilliseconds())
        logger.trace(
            "schedule check:{} at:{} with timer:{}",
            traceKey,
            checkDelay.toLocalDateTime(GlobalData.zoneId),
            timerId
        )
        timers[timerId] = traceKey
    }

    private fun traceEntityByRoot(entity: TraceableRootEntity<*>) {
        val entityClazz = entity::class
        val root = requireNotNull(entityClazz.simpleName) { "class simple name is null" }.upperCamelToLowerCamel()
        val traceKey = TraceKey("$root.${entity.key()}", TraceType.NormalField)
        val traceData = TraceData.of(entity, hashFunction)
        scheduleTraceCheck(traceKey, traceData)
        logger.info("{}", traceData)
        traceMap[traceKey] = traceData
        logger.debug("trace entity:{} by root", entityClazz)
    }

    /**
     * @return true if hash changed
     */
    private fun calHashCode(traceData: TraceData): Boolean {
        val preHashCode = traceData.hashCode
        traceData.hashCode = traceData.data.hashCode()
        return preHashCode != traceData.hashCode
    }

    /**
     * @return true if hash changed
     */
    private fun calFullHashCode(traceData: TraceData): Boolean {
        val preHashCode = traceData.fullHashCode
        traceData.fullHashCode = hashFunction.hashBytes(Json.toJsonBytes(traceData.data))
        return preHashCode != traceData.fullHashCode
    }

    private fun nextCheckTime(traceData: TraceData): Instant {
        val delay = 1.seconds - traceData.sameHashCount.seconds + (Random.nextInt(1..2).seconds)
        return clock.now().plus(delay)
    }

    fun tick(now: Instant) {
        timerWheel.poll(now.toEpochMilliseconds(), ::handleTimeout, 1000)
    }

    private fun handleTimeout(timeUnit: TimeUnit, now: Long, timerId: Long): Boolean {
        val traceKey = timers.remove(timerId)
        if (traceKey != null) {
            logger.trace(
                "{} timerId:{} timeout at:{}",
                traceKey,
                timerId,
                Instant.fromEpochMilliseconds(now).toLocalDateTime(GlobalData.zoneId)
            )
            val traceData = requireNotNull(traceMap[traceKey]) { "track data of key:$traceKey not found" }
            when (traceKey.type) {
                TraceType.MapField -> {
                    val (delete, save, update) = checkMapFieldHash(traceKey, traceData)
                    delete.forEach { (k, v) ->
                        checkNotNull(traceMap.remove(k))
                        persistent(Operation.Delete, k, v)
                    }
                    save.forEach { (k, v) ->
                        val mapValueData = TraceData.of(v, hashFunction)
                        check(this.traceMap.containsKey(k).not())
                        traceMap[k] = mapValueData
                        persistent(Operation.Save, k, mapValueData)
                    }
                    update.forEach { (k, v) ->
                        check(this.traceMap.containsKey(k))
                        persistent(Operation.Update, k, v)
                    }
                    scheduleTraceCheck(traceKey, traceData)
                }

                TraceType.NormalField,
                TraceType.MapValue -> {
                    val operation = checkDataHash(traceData)
                    if (operation != null) {
                        persistent(operation, traceKey, traceData)
                    }
                    scheduleTraceCheck(traceKey, traceData)
                }
            }
        } else {
            logger.warn("track key of timerId:{} not found", timerId)
        }
        return true
    }

    internal fun checkDataHash(traceData: TraceData): Operation? {
        if (traceData.data == null) {
            with(traceData) {
                sameHashCount = 0
                hashCode = 0
                fullHashCode = HashCode.fromInt(0)
            }
            return Operation.Delete
        }
        val hashChanged = calHashCode(traceData)
        if (!hashChanged) {
            traceData.sameHashCount++
        } else {
            traceData.sameHashCount = 0
            return Operation.Update
        }
        if (traceData.sameHashCount >= fullHashThreshold) {
            val fullHashChanged = calFullHashCode(traceData)
            if (fullHashChanged) {
                return Operation.Update
            }
            traceData.sameHashCount = 0
        }
        return null
    }

    /**
     * @return first: delete data, second: save data, third: update data
     */
    internal fun checkMapFieldHash(
        traceKey: TraceKey,
        traceData: TraceData
    ): Triple<Map<TraceKey, TraceData>, Map<TraceKey, Any>, Map<TraceKey, TraceData>> {
        check(traceKey.type == TraceType.MapField)
        val mapData = traceData.data as Map<*, *>
        val allMapValue = mutableMapOf<TraceKey, Any>()
        mapData.forEach { (k, v) ->
            checkNotNull(k) { "$traceKey map key is null" }
            //if map value is null, represent this value deleted
            if (v != null) {
                allMapValue[TraceKey("${traceKey.path}.$k", TraceType.MapValue)] = v
            }
        }
        val tracedMapValue = mutableSetOf<TraceKey>()
        val delete = mutableMapOf<TraceKey, TraceData>()
        val update = mutableMapOf<TraceKey, TraceData>()
        traceMap.forEach { (k, v) ->
            if (k.type == TraceType.MapValue && k.path.startsWith(traceKey.path)) {
                tracedMapValue.add(k)
                val maybeTraced = allMapValue[k]
                if (maybeTraced != null && (maybeTraced !== v.data || checkDataHash(v) == Operation.Update)) {
                    update[k] = v
                } else if (maybeTraced == null) {
                    delete[k] = v
                }
            }
        }
        val save = allMapValue.filter { it.key !in tracedMapValue }
        return Triple(delete, save, update)
    }

    private fun persistent(operation: Operation, traceKey: TraceKey, traceData: TraceData) {
        merge(operation, traceKey, traceData)
        val submittingData = arrayListOf<Pair<TraceKey, PersistentDocument>>()
        val iter = pendingData.iterator()
        while (iter.hasNext()) {
            val next = iter.next()
            val (submitting, pending) = next.value
            if (submitting == null && pending != null) {
                submittingData.add(next.key to pending)
                next.setValue(pending to null)
            }
        }
        submittingData.forEach { (k, v) ->
            try {
                when (v.operation) {
                    Operation.Save,
                    Operation.Update -> {
                        val update = Update.update(k.path.removePrefix("room.1."), v.document)
                        val result = template.updateFirst(Query.query(Criteria.where("_id").`is`(1)), update, "room")
                    }

                    Operation.Delete -> {
                        val update = Update().unset(k.path.removePrefix("room.1."))
                        val result = template.updateFirst(Query.query(Criteria.where("_id").`is`(1)), update, "room")
                    }
                }
            } catch (e: Exception) {
                logger.error("", e)
            }
        }
    }

    private fun merge(incomingOperation: Operation, traceKey: TraceKey, traceData: TraceData) {
        val (submitting, pending) = pendingData[traceKey] ?: (null to null)
        if (pending == null) {
            val persistentDoc = genPersistentDoc(incomingOperation, traceData)
            pendingData[traceKey] = submitting to persistentDoc
            logger.trace("put pending key:{} {} ", traceKey, incomingOperation)
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
        val persistentDoc = genPersistentDoc(mergedOperation, traceData)
        pendingData[traceKey] = submitting to persistentDoc
        logger.trace(
            "merge pending key:{} incoming:{} pending:{} final:{}",
            traceKey,
            pending.operation,
            incomingOperation,
            mergedOperation
        )
    }

    private fun genPersistentDoc(incomingOperation: Operation, traceData: TraceData): PersistentDocument {
        return when (incomingOperation) {
            Operation.Save,
            Operation.Update -> {
                val document = Document()
                val data = checkNotNull(traceData.data) { "save or update data cannot be null" }
                template.converter.write(data, document)
                PersistentDocument(incomingOperation, SubmitStatus.Pending, document)
            }

            Operation.Delete -> {
                PersistentDocument(incomingOperation, SubmitStatus.Pending, null)
            }
        }
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
    }
}