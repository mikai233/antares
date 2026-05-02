package com.mikai233.world

import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import io.github.mikai233.asteria.persistence.DataModule
import io.github.mikai233.asteria.persistence.MemData
import io.github.mikai233.asteria.persistence.dataModule
import org.springframework.data.mongodb.core.MongoTemplate

val WorldDataModules: List<DataModule<Long, out MemData>> = listOf(
    dataModule<Long, PlayerAbstractMem> { scope ->
        PlayerAbstractMem(scope.entityId, scope.mongoTemplateProvider())
    },
    dataModule<Long, WorldActionMem> { scope ->
        WorldActionMem(scope.entityId, scope.mongoTemplateProvider())
    },
)

private fun io.github.mikai233.asteria.persistence.DataScope<Long>.mongoTemplateProvider(): () -> MongoTemplate {
    return { services.get(MongoTemplate::class) }
}
