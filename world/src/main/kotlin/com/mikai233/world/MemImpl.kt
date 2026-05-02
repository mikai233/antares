package com.mikai233.world

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import io.github.mikai233.asteria.persistence.DataModule
import io.github.mikai233.asteria.persistence.MemData
import io.github.mikai233.asteria.persistence.dataModule
import org.springframework.data.mongodb.core.MongoTemplate

val WorldDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerAbstractMem> { scope ->
        PlayerAbstractMem(scope.entityId, scope.mongoTemplateProvider(), scope.mongoDatabaseProvider())
    },
    dataModule<Long, WorldActionMem> { scope ->
        WorldActionMem(scope.entityId, scope.mongoTemplateProvider(), scope.mongoDatabaseProvider())
    },
)

private fun io.github.mikai233.asteria.persistence.DataScope<Long>.mongoTemplateProvider(): () -> MongoTemplate {
    return { services.get(MongoTemplate::class) }
}

private fun io.github.mikai233.asteria.persistence.DataScope<Long>.mongoDatabaseProvider(): () -> MongoDatabase {
    return { services.get(MongoDatabase::class) }
}
