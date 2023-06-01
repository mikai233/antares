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
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
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
    val data: Any,
    var hashCode: Int,
    var fullHashCode: HashCode,
    var sameHashCount: Int
) {
    companion object {
        fun of(data: Any, hashFunction: HashFunction): TraceData {
            return TraceData(data, data.hashCode(), hashFunction.hashBytes(Json.toJsonBytes(data)), 0)
        }
    }
}

class TrackableMemCacheDB(private val template: MongoTemplate) {
    private val logger = logger()
    private val clock = Clock.System
    private val pendingData: MutableMap<TraceKey, Pair<PersistentDocument?, PersistentDocument?>> = mutableMapOf()
    private val traceMap: MutableMap<TraceKey, TraceData> = mutableMapOf()
    private val timers: BiMap<Long, TraceKey> = HashBiMap.create()
    private val hashFunction = Hashing.goodFastHash(128)
    private val timerWheel = DeadlineTimerWheel(TimeUnit.MILLISECONDS, unixTimestamp(), 16, 256)
    private val fullHashThreshold = 100

    fun traceEntity(entity: Entity<*>) {
        when (entity) {
            is FieldTraceableEntity -> {
                traceEntityByFields(entity)
            }

            is TraceableEntity -> {
                traceEntityByRoot(entity)
            }
        }
    }

    private fun traceEntityByFields(entity: FieldTraceableEntity<*>) {
        val entityClazz = entity::class
        val root = requireNotNull(entityClazz.simpleName) { "class simple name is null" }.upperCamelToLowerCamel()
        entityClazz.declaredMemberProperties.forEach { kp ->
            val returnType = kp.returnType
            val fieldValue = checkNotNull(kp.call(entity)) { "field value is not allowed nullable" }
            when {
                returnType.isSubtypeOf(typeOf<Map<*, *>>()) -> {
                    val mapKey = TraceKey("$root.${kp.name}", TraceType.MapField)
                    val mapField = fieldValue as Map<*, *>
                    traceMap[mapKey] = TraceData.of(mapField, hashFunction)
                    mapField.forEach { (k, v) ->
                        checkNotNull(k) { "map key of:${kp.name} in $entityClazz is not allowed nullable" }
                        checkNotNull(v) { "map value of:${kp.name} in $entityClazz is not allowed nullable" }
                        val mapValueKey = TraceKey("${mapKey.path}.$k", TraceType.MapValue)
                        traceMap[mapValueKey] = TraceData.of(v, hashFunction)
                    }
                }

                else -> {
                    val fieldKey = TraceKey("$root.${kp.name}", TraceType.NormalField)
                    traceMap[fieldKey] = TraceData.of(fieldValue, hashFunction)
                }
            }
        }
        traceMap.forEach(::scheduleTraceCheck)
        logger.info("{}", traceMap)
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

    private fun traceEntityByRoot(entity: TraceableEntity<*>) {

    }

    private fun fillTrackMap() {

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
        val delay = 1.seconds - traceData.sameHashCount.seconds + (Random.nextInt(10..20).seconds)
        return clock.now().plus(delay)
    }

    fun tick(now: Instant) {
        timerWheel.poll(now.toEpochMilliseconds(), ::handleTimeout, 1000)
    }

    private fun handleTimeout(timeUnit: TimeUnit, now: Long, timerId: Long): Boolean {
        val trackKey = timers.remove(timerId)
        if (trackKey != null) {
            logger.trace(
                "{} timerId:{} timeout at:{}",
                trackKey,
                timerId,
                Instant.fromEpochMilliseconds(now).toLocalDateTime(GlobalData.zoneId)
            )
            val traceData = requireNotNull(traceMap[trackKey]) { "track data of key:$trackKey not found" }
            when (trackKey.type) {
                TraceType.MapField -> {
                    checkMapFieldHash(trackKey, traceData)
                }

                TraceType.NormalField,
                TraceType.MapValue -> {
                    checkFieldHash(trackKey, traceData)
                }
            }
        } else {
            logger.warn("track key of timerId:{} not found", timerId)
        }
        return true
    }

    private fun checkFieldHash(traceKey: TraceKey, traceData: TraceData) {
        val hashChanged = calHashCode(traceData)
        if (!hashChanged) {
            traceData.sameHashCount++
        } else {
            traceData.sameHashCount = 0
            genPersistentCmd(Operation.Update, traceKey, traceData)
        }
        if (traceData.sameHashCount >= fullHashThreshold) {
            val fullHashChanged = calFullHashCode(traceData)
            if (fullHashChanged) {
                genPersistentCmd(Operation.Update, traceKey, traceData)
            }
            traceData.sameHashCount = 0
        }
        scheduleTraceCheck(traceKey, traceData)
    }

    private fun checkMapFieldHash(traceKey: TraceKey, traceData: TraceData) {
        check(traceKey.type == TraceType.MapField)
        val mapData = traceData.data as Map<*, *>
        val allMapValue = mutableMapOf<TraceKey, Any>()
        mapData.forEach { (k, v) ->
            checkNotNull(k) { "$traceKey map key is null" }
            checkNotNull(v) { "$traceKey map value is null" }
            allMapValue[TraceKey("${traceKey.path}.$k", TraceType.MapValue)] = v
        }
        val trackedMapValue =
            this.traceMap.filter { it.key.type == TraceType.MapValue && it.key.path.startsWith(traceKey.path) }
        val delete = trackedMapValue.filter { it.key !in allMapValue }
        val save = allMapValue.filter { it.key !in trackedMapValue }
        delete.forEach { (k, v) ->
            timers.inverse().remove(k)?.let { timerId ->
                timerWheel.cancelTimer(timerId)
            }
            checkNotNull(traceMap.remove(k))
            genPersistentCmd(Operation.Delete, k, v)
        }
        save.forEach { (k, v) ->
            val mapValueData = TraceData.of(v, hashFunction)
            scheduleTraceCheck(k, mapValueData)
            check(this.traceMap.containsKey(k).not())
            traceMap[k] = mapValueData
            genPersistentCmd(Operation.Save, k, mapValueData)
        }
        scheduleTraceCheck(traceKey, traceData)
    }

    private fun genPersistentCmd(operation: Operation, traceKey: TraceKey, traceData: TraceData) {
        merge(operation, traceKey, traceData)
    }

    private fun merge(incomingOperation: Operation, traceKey: TraceKey, traceData: TraceData) {
        val (submitting, pending) = pendingData[traceKey] ?: (null to null)
        val document = Document()
        template.converter.write(traceData.data, document)
        if (pending == null) {
            val persistentDocument = PersistentDocument(incomingOperation, SubmitStatus.Pending, document)
            pendingData[traceKey] = submitting to persistentDocument
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
        val persistentDocument = PersistentDocument(mergedOperation, SubmitStatus.Pending, document)
        pendingData[traceKey] = submitting to persistentDocument
        logger.trace(
            "merge:{} pending:{} incoming:{} final:{}",
            traceKey,
            pending.operation,
            incomingOperation,
            mergedOperation
        )
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
    db.traceEntity(room)
    room.players.clear()
    room.players[3] = RoomPlayer(12, 12)
    while (true) {
        Thread.sleep(100)
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