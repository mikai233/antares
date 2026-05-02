package com.mikai233.gm

import com.mikai233.gm.web.GmHttpServer
import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorSystem

class GmRuntimeModule(
    private val node: GmNode,
) : AsteriaModule {
    override val name: String = "gm-runtime"

    private var httpServer: GmHttpServer? = null

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        system.actorOf(MonitorActor.props(node), "monitorActor")
        httpServer = GmHttpServer(node).also { it.start() }
    }

    override suspend fun stop(context: ModuleContext) {
        httpServer?.stop()
        httpServer = null
    }
}
