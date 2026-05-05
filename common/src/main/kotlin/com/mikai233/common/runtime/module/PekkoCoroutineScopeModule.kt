package com.mikai233.common.runtime.module

import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.apache.pekko.actor.ActorSystem

class PekkoCoroutineScopeModule : AsteriaModule {
    override val name: String = "pekko-coroutine-scope"

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        val scope = CoroutineScope(system.dispatcher.asCoroutineDispatcher() + SupervisorJob())
        context.services.register(CoroutineScope::class, scope)
    }

    override suspend fun stop(context: ModuleContext) {
        context.services.find(CoroutineScope::class)?.cancel()
    }
}
