package com.mikai233.player

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerMem
import io.github.mikai233.asteria.core.ServiceRegistry
import io.github.mikai233.asteria.persistence.DataModule
import io.github.mikai233.asteria.persistence.MemData
import io.github.mikai233.asteria.persistence.dataModule
import org.springframework.data.mongodb.core.MongoTemplate

val PlayerDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerActionMem> { scope ->
        PlayerActionMem(scope.entityId, scope.mongoTemplateProvider(), scope.mongoDatabaseProvider())
    },
    dataModule<Long, PlayerMem> { scope ->
        PlayerMem(scope.entityId, scope.mongoTemplateProvider(), scope.mongoDatabaseProvider())
    },
)

private fun io.github.mikai233.asteria.persistence.DataScope<Long>.mongoTemplateProvider(): () -> MongoTemplate {
    return { services.get(MongoTemplate::class) }
}

private fun io.github.mikai233.asteria.persistence.DataScope<Long>.mongoDatabaseProvider(): () -> MongoDatabase {
    return { services.get(MongoDatabase::class) }
}
