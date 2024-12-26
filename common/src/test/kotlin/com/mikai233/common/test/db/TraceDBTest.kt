package com.mikai233.common.test.db

import com.mikai233.common.core.actor.ActorCoroutine
import com.mikai233.common.db.*
import com.mikai233.common.entity.*
import com.mongodb.client.MongoClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.Test
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.concurrent.Executor

class TraceDBTest {
    data class ChildData(val field1: String, var field2: Long)
    data class RootEntity(
        @Id
        val id: Long,
        val field1: String,
        val field2: HashMap<Int, Int>,
        var field3: HashMap<Int, Int>,
        val field4: MutableList<String>,
        var field5: HashMap<Int, String?>,
        var field6: ChildData?,
    ) : Entity

    data class TraceFieldEntity(
        val id: Long,
        var field1: Int,
        var field2: MutableList<Int>,
        val field3: MutableMap<Int, Int?>,
        var field4: MutableSet<Int>?,
        var field5: ChildData?,
    )

    @Test
    fun traceRootEntity() {
        val client = MongoClients.create()
        val template = MongoTemplate(SimpleMongoClientDatabaseFactory(client, "test"))
        val db = Tracer(
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
        val query = Query.query(Criteria.where("_id").`is`(entity.id()))
        val key = TKey(root, query, null, TType.Data)
        db.traceEntity(entity)
        entity.field3 = hashMapOf()
        var operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Status.Unset)
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == null)
        entity.field6 = ChildData("hello", 1L)
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Status.Unset)
        entity.field6?.field2 = 2
        operation = db.checkDataHash(traceMap[key]!!)
        assert(operation == Status.Unset)
    }

    @Test
    fun traceFieldEntity() {
    }
}
