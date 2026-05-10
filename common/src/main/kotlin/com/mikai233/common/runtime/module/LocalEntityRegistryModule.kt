package com.mikai233.common.runtime.module

import com.mikai233.common.runtime.LocalEntityRegistry
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext

class LocalEntityRegistryModule : AsteriaModule {
    override val name: String = "local-entity-registry"

    override suspend fun install(context: ModuleContext) {
        context.services.register(LocalEntityRegistry::class, LocalEntityRegistry())
    }
}
