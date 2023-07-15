package com.mikai233.common.test.db

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.db.*
import com.mikai233.common.entity.TraceableFieldEntity
import com.mikai233.common.entity.TraceableRootEntity
import com.mongodb.client.MongoClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.Executor

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
        val db = DataTracer(
            { template },
            ActorCoroutine(CoroutineScope(Executor { it.run() }.asCoroutineDispatcher())),
            fullHashThreshold = 1
        )
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
        val root = entity::class
        val query = Query.query(Criteria.where("_id").`is`(entity.key()))
        val key = TKey(root, query, null, TType.Data)
        db.traceEntity(entity)
        entity.field3 = hashMapOf()
        var operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Operation.Update)
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == null)
        entity.field6 = ChildData("hello", 1L)
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Operation.Update)
        entity.field6?.field2 = 2
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Operation.Update)
    }

    @Test
    fun traceFieldEntity() {
        val client = MongoClients.create()
        val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
        val db = DataTracer(
            { template },
            ActorCoroutine(CoroutineScope(Executor { it.run() }.asCoroutineDispatcher())),
            fullHashThreshold = 1
        )
        val traceMap = db.traceMap
        val entity = TraceFieldEntity(
            id = 1,
            field1 = 12,
            field2 = mutableListOf(1, 2, 3),
            field3 = mutableMapOf(1 to 1),
            field4 = null,
            field5 = ChildData("mikai", 12),
        )
        val root = entity::class
        val query = Query.query(Criteria.where("_id").`is`(entity.key()))
        db.traceEntity(entity)
        entity.field3.clear()
        entity.field3[2] = 2
        val field3Path = TraceFieldEntity::field3.name
        val keyOfField3 = TKey(root, query, field3Path, TType.Map)
        val (delete, add, update) = db.checkMapValueHash(keyOfField3, traceMap[keyOfField3]!!)
        assert(delete.size == 1)
        assert(delete[TKey(root, query, "$field3Path.1", TType.Builtin)] != null)
        delete.forEach {
            traceMap.remove(it.key)
        }
        assert(add.size == 1)
        assert(add[TKey(root, query, "$field3Path.2", TType.Builtin)] != null)
        add.forEach {
            traceMap[it.key] = TData(it.value, it.hashCode(), db.serdeHash(it.value), 0)
        }
        assert(update.isEmpty())
        entity.field3[1] = 3
        val (delete1, save1, update1) = db.checkMapValueHash(keyOfField3, traceMap[keyOfField3]!!)
        assert(delete1.isEmpty())
        assert(save1.size == 1)
        assert(save1[TKey(root, query, "$field3Path.1", TType.Builtin)] != null)
        save1.forEach {
            traceMap[it.key] = TData(it, it.hashCode(), db.serdeHash(it), 0)
        }
        assert(update.isEmpty())
        entity.field3[1] = 4
        val (delete2, save2, update2) = db.checkMapValueHash(keyOfField3, traceMap[keyOfField3]!!)
        assert(delete2.isEmpty())
        assert(save2.isEmpty())
        assert(update2.size == 1)
        entity.field4 = mutableSetOf(1, 2, 3)
        val field4Path = TraceFieldEntity::field4.name
        val keyOfField4 = TKey(root, query, field4Path, TType.Builtin)
        val data = traceMap[keyOfField4]!!
        data.inner = entity.field4
        val operation = db.checkDataHash(data)
        assert(operation == Operation.Update)
    }
}
