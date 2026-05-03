package com.mikai233.world

import com.mikai233.common.db.MongoDB
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import io.github.realmlabs.asteria.persistence.DataModule
import io.github.realmlabs.asteria.persistence.MemData
import io.github.realmlabs.asteria.persistence.dataModule

val WorldDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerAbstractMem> { scope ->
        PlayerAbstractMem(scope.entityId, scope.mongoDbProvider())
    },
    dataModule<Long, WorldActionMem> { scope ->
        WorldActionMem(scope.entityId, scope.mongoDbProvider())
    },
)

private fun io.github.realmlabs.asteria.persistence.DataScope<Long>.mongoDbProvider(): () -> MongoDB {
    return { services.get(MongoDB::class) }
}
