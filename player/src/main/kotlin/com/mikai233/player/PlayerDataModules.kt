package com.mikai233.player

import com.mikai233.common.config.ActorConfigSyncMem
import com.mikai233.common.db.MongoDB
import com.mikai233.common.time.GameTime
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerActivityMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.persistence.DataModule
import io.github.realmlabs.asteria.persistence.MemData
import io.github.realmlabs.asteria.persistence.dataModule

val PlayerDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, ActorConfigSyncMem> { scope ->
        ActorConfigSyncMem("player", scope.entityId.toString(), scope.mongoDbProvider(), scope.gameTimeProvider())
    },
    dataModule<Long, PlayerActivityMem> { scope ->
        PlayerActivityMem(scope.entityId, scope.mongoDbProvider())
    },
    dataModule<Long, PlayerActionMem> { scope ->
        PlayerActionMem(scope.entityId, scope.mongoDbProvider())
    },
    dataModule<Long, PlayerMem> { scope ->
        PlayerMem(scope.entityId, scope.mongoDbProvider())
    },
)

private fun io.github.realmlabs.asteria.persistence.DataScope<Long>.mongoDbProvider(): () -> MongoDB {
    return { services.get(MongoDB::class) }
}

private fun io.github.realmlabs.asteria.persistence.DataScope<Long>.gameTimeProvider(): () -> GameTime {
    return { services.get(GameTime::class) }
}
