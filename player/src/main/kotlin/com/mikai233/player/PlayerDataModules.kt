package com.mikai233.player

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.persistence.DataModule
import io.github.realmlabs.asteria.persistence.MemData
import io.github.realmlabs.asteria.persistence.dataModule

val PlayerDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerActionMem> { scope ->
        PlayerActionMem(scope.entityId, scope.mongoDatabaseProvider())
    },
    dataModule<Long, PlayerMem> { scope ->
        PlayerMem(scope.entityId, scope.mongoDatabaseProvider())
    },
)

private fun io.github.realmlabs.asteria.persistence.DataScope<Long>.mongoDatabaseProvider(): () -> MongoDatabase {
    return { services.get(MongoDatabase::class) }
}
