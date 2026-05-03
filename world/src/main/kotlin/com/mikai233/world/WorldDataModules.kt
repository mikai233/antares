package com.mikai233.world

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import io.github.realmlabs.asteria.persistence.DataModule
import io.github.realmlabs.asteria.persistence.MemData
import io.github.realmlabs.asteria.persistence.dataModule

val WorldDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerAbstractMem> { scope ->
        PlayerAbstractMem(scope.entityId, scope.mongoDatabaseProvider())
    },
    dataModule<Long, WorldActionMem> { scope ->
        WorldActionMem(scope.entityId, scope.mongoDatabaseProvider())
    },
)

private fun io.github.realmlabs.asteria.persistence.DataScope<Long>.mongoDatabaseProvider(): () -> MongoDatabase {
    return { services.get(MongoDatabase::class) }
}
