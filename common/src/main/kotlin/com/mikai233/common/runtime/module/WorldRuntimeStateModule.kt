package com.mikai233.common.runtime.module

import com.mikai233.common.runtime.WorldRuntimeStateStore
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext

class WorldRuntimeStateModule : AsteriaModule {
    override val name: String = "world-runtime-state"

    override suspend fun install(context: ModuleContext) {
        context.services.register(
            WorldRuntimeStateStore::class,
            WorldRuntimeStateStore(context.services.get(RuntimeConfigRepository::class)),
        )
    }
}
