package com.mikai233.common.db

import com.mikai233.common.entity.*
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.unixTimestamp
import com.mongodb.client.MongoClients
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

class PersistentDocument(
    val operation: Operation,
    val status: SubmitStatus,
    val document: Document
)

enum class Operation {
    Save,
    Update,
    Delete,
}

enum class SubmitStatus {
    Pending,
    Submitting,
}

enum class TrackType {
    NormalField,
    MapField,
    MapValue,
}

class TrackKey(val path: String, type: TrackType)

class TrackData(val data: Any, val hashCode: Int, val fullHashCode: Int, val sameHashCount: Int)

class TrackableMemCacheDB(private val template: MongoTemplate) {
    private val logger = logger()
    private val pendingQueue: MutableMap<String, ArrayDeque<PersistentDocument>> = mutableMapOf()
    private val trackMap: MutableMap<TrackKey, TrackData> = mutableMapOf()

    fun trackEntity(entity: Entity) {

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