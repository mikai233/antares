package com.mikai233.common.db

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.mikai233.common.entity.*
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.unixTimestamp
import com.mikai233.common.ext.upperCamelToLowerCamel
import com.mongodb.client.MongoClients
import org.agrona.DeadlineTimerWheel
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

enum class TrackType {
    NormalField,
    MapField,
    MapValue,
}

data class TrackKey(val path: String, val type: TrackType)

data class TrackData(val data: Any, var hashCode: Int, var fullHashCode: HashCode, var sameHashCount: Int)

class TrackableMemCacheDB(private val template: MongoTemplate) {
    private val logger = logger()
    private val pendingQueue: MutableMap<String, ArrayDeque<PersistentDocument>> = mutableMapOf()
    private val trackMap: MutableMap<TrackKey, TrackData> = mutableMapOf()
    private val timerMap: MutableMap<Long, TrackKey> = mutableMapOf()
    private val hashFunction = Hashing.goodFastHash(128)
    private val timerWheel = DeadlineTimerWheel(TimeUnit.MILLISECONDS, unixTimestamp(), 128, 16)

    fun trackEntity(entity: Entity<*>) {
        when (entity) {
            is FieldTrackableEntity -> {
                trackEntityByFields(entity)
            }

            is TrackableEntity -> {
                trackEntityByRoot(entity)
            }
        }
    }

    private fun trackEntityByFields(entity: FieldTrackableEntity<*>) {
        val entityClazz = entity::class
        val root = requireNotNull(entityClazz.simpleName) { "class simple name is null" }.upperCamelToLowerCamel()
        entityClazz.declaredMemberProperties.forEach { kp ->
            val returnType = kp.returnType
            val fieldValue = checkNotNull(kp.call(entity)) { "field value is not allowed nullable" }
            when {
                returnType.isSubtypeOf(typeOf<Map<*, *>>()) -> {
                    val mapKey = TrackKey("$root.${kp.name}", TrackType.MapField)
                    val mapField = fieldValue as Map<*, *>
                    trackMap[mapKey] = TrackData(mapField, 0, HashCode.fromInt(0), 0)
                    mapField.forEach { (k, v) ->
                        checkNotNull(k) { "map key of:${kp.name} in $entityClazz is not allowed nullable" }
                        checkNotNull(v) { "map value of:${kp.name} in $entityClazz is not allowed nullable" }
                        val mapValueKey = TrackKey("${mapKey.path}.$k", TrackType.MapValue)
                        trackMap[mapValueKey] = TrackData(v, 0, HashCode.fromInt(0), 0)
                    }
                }

                else -> {
                    val fieldKey = TrackKey("$root.${kp.name}", TrackType.NormalField)
                    trackMap[fieldKey] = TrackData(fieldValue, 0, HashCode.fromInt(0), 0)
                }
            }
        }
        trackMap.forEach { (trackKey, trackData) ->
            calHashCode(trackData)
            calFullHashCode(trackData)
            val timerId = timerWheel.scheduleTimer(nextCheckTime(trackData))
            timerMap[timerId] = trackKey
        }
        logger.info("{}", trackMap)
    }

    private fun trackEntityByRoot(entity: TrackableEntity<*>) {

    }

    private fun fillTrackMap() {

    }

    private fun calHashCode(trackData: TrackData): Boolean {
        val preHashCode = trackData.hashCode
        trackData.hashCode = trackData.data.hashCode()
        return preHashCode == trackData.hashCode
    }

    private fun calFullHashCode(trackData: TrackData): Boolean {
        val preHashCode = trackData.fullHashCode
        trackData.fullHashCode = hashFunction.hashBytes(Json.toJsonBytes(trackData.data))
        return preHashCode == trackData.fullHashCode
    }

    private fun nextCheckTime(trackData: TrackData): Long {
        val delay = 1.minutes - trackData.sameHashCount.seconds + (Random.nextInt(10..20).seconds)
        return delay.toLong(DurationUnit.MILLISECONDS)
    }

    fun tick() {

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
    db.trackEntity(room)
}