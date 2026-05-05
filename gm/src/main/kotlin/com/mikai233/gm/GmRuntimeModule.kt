package com.mikai233.gm

import com.mikai233.common.config.DATA_SOURCE_GAME
import com.mikai233.common.config.DataSourceConfig
import com.mikai233.gm.web.GmHttpServer
import io.github.realmlabs.asteria.config.center.RuntimeConfigRepository
import io.github.realmlabs.asteria.core.AsteriaModule
import io.github.realmlabs.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorSystem

class GmRuntimeModule(
    private val node: GmNode,
) : AsteriaModule {
    override val name: String = "gm-runtime"

    private var httpServer: GmHttpServer? = null

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        system.actorOf(MonitorActor.props(node), "monitorActor")
        val dataSource = context.services.get(RuntimeConfigRepository::class)
            .get<DataSourceConfig>(DATA_SOURCE_GAME)
            ?.value
            ?: error("runtime config $DATA_SOURCE_GAME not found")
        httpServer = GmHttpServer(node, dataSource).also { it.start() }
    }

    override suspend fun stop(context: ModuleContext) {
        httpServer?.stop()
        httpServer = null
    }
}
