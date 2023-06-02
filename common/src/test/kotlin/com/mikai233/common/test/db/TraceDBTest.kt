package com.mikai233.common.test.db

import com.mikai233.common.db.*
import com.mikai233.common.entity.TraceableFieldEntity
import com.mikai233.common.entity.TraceableRootEntity
import com.mongodb.client.MongoClients
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

class TraceDBTest {
    data class ChildData(val field1: String, var field2: Long)
    data class RootEntity(
        val id: Long,
        val field1: String,
        val field2: HashMap<Int, Int>,
        var field3: HashMap<Int, Int>,
        val field4: MutableList<String>,
        var field5: HashMap<Int, String?>,
        var field6: ChildData?,
    ) : TraceableRootEntity<Long> {
        override fun key(): Long {
            return id
        }
    }

    data class TraceFieldEntity(
        val id: Long,
        var field1: Int,
        var field2: MutableList<Int>,
        val field3: MutableMap<Int, Int?>,
        var field4: MutableSet<Int>?,
        var field5: ChildData?,
    ) : TraceableFieldEntity<Long> {
        override fun key(): Long {
            return id
        }
    }

    @Test
    fun traceRootEntity() {
        val client = MongoClients.create()
        val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
        val db = TrackableMemCacheDB(template, 1)
        val traceMap = db.traceMap
        val entity = RootEntity(
            id = 1,
            field1 = "mikai",
            field2 = hashMapOf(),
            field3 = hashMapOf(1 to 1, 2 to 2),
            field4 = mutableListOf(),
            field5 = hashMapOf(1 to null, 2 to "hello"),
            field6 = null,
        )
        val path = "rootEntity.1"
        db.traceEntity(entity)
        entity.field3 = hashMapOf()
        var operation = db.checkDataHash(traceMap[TraceKey(path, TraceType.NormalField)]!!)
        assert(operation == Operation.Update)
        operation = db.checkDataHash(traceMap[TraceKey(path, TraceType.NormalField)]!!)
        assert(operation == null)
        entity.field6 = ChildData("hello", 1L)
        operation = db.checkDataHash(traceMap[TraceKey(path, TraceType.NormalField)]!!)
        assert(operation == Operation.Update)
        entity.field6?.field2 = 2
        operation = db.checkDataHash(traceMap[TraceKey(path, TraceType.NormalField)]!!)
        assert(operation == Operation.Update)
    }

    @Test
    fun traceFieldEntity() {
        val client = MongoClients.create()
        val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
        val db = TrackableMemCacheDB(template, 1)
        val traceMap = db.traceMap
        val entity = TraceFieldEntity(
            id = 1,
            field1 = 12,
            field2 = mutableListOf(1, 2, 3),
            field3 = mutableMapOf(1 to 1),
            field4 = null,
            field5 = ChildData("mikai", 12),
        )
        db.traceEntity(entity)
        entity.field3.clear()
        entity.field3[2] = 2
        val traceKey = TraceKey("traceFieldEntity.1.field3", TraceType.MapField)
        val (delete, save, update) = db.checkMapFieldHash(traceKey, traceMap[traceKey]!!)
        assert(delete.size == 1)
        assert(delete[TraceKey("traceFieldEntity.1.field3.1", TraceType.MapValue)] != null)
        delete.forEach {
            traceMap.remove(it.key)
        }
        assert(save.size == 1)
        assert(save[TraceKey("traceFieldEntity.1.field3.2", TraceType.MapValue)] != null)
        save.forEach {
            traceMap[it.key] = TraceData.of(it.value, db.hashFunction)
        }
        assert(update.isEmpty())
        entity.field3[1] = 3
        val (delete1, save1, update1) = db.checkMapFieldHash(traceKey, traceMap[traceKey]!!)
        assert(delete1.isEmpty())
        assert(save1.size == 1)
        assert(save1[TraceKey("traceFieldEntity.1.field3.1", TraceType.MapValue)] != null)
        save1.forEach {
            traceMap[it.key] = TraceData.of(it.value, db.hashFunction)
        }
        assert(update.isEmpty())
        entity.field3[1] = 4
        val (delete2, save2, update2) = db.checkMapFieldHash(traceKey, traceMap[traceKey]!!)
        assert(delete2.isEmpty())
        assert(save2.isEmpty())
        assert(update2.size == 1)
    }
}