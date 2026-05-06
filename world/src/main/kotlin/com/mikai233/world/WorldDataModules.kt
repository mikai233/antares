package com.mikai233.world

import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.common.db.MongoDB
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import io.github.realmlabs.asteria.persistence.DataModule
import io.github.realmlabs.asteria.persistence.DataScope
import io.github.realmlabs.asteria.persistence.MemData
import io.github.realmlabs.asteria.persistence.dataModule

val WorldDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, ActorConfigSyncMem> { scope ->
        ActorConfigSyncMem("world", scope.entityId.toString(), scope.mongoDbProvider())
    },
    dataModule<Long, PlayerAbstractMem> { scope ->
        PlayerAbstractMem(scope.entityId, scope.mongoDbProvider())
    },
    dataModule<Long, WorldActionMem> { scope ->
        WorldActionMem(scope.entityId, scope.mongoDbProvider())
    },
)

private fun DataScope<Long>.mongoDbProvider(): () -> MongoDB {
    return { services.get(MongoDB::class) }
}
