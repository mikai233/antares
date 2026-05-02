package com.mikai233.gm

import com.mikai233.gm.script.ScriptExecutionManagerActor
import com.mikai233.gm.web.GmWebServer
import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.routing.FromConfig

class GmRuntime(
    val scriptRouter: ActorRef,
    val scriptExecutionManager: ActorRef,
)

class GmRuntimeModule(
    private val node: GmNode,
) : AsteriaModule {
    override val name: String = "gm-runtime"

    private var webServer: GmWebServer? = null

    override suspend fun start(context: ModuleContext) {
        val system = context.services.get(ActorSystem::class)
        val scriptRouter = system.actorOf(FromConfig.getInstance().props(), "scriptActorRouter")
        system.actorOf(MonitorActor.props(node), "monitorActor")
        val scriptExecutionManager = system.actorOf(
            ScriptExecutionManagerActor.props(node),
            ScriptExecutionManagerActor.NAME,
        )
        context.services.register(GmRuntime::class, GmRuntime(scriptRouter, scriptExecutionManager))

        webServer = GmWebServer(node).also { it.start() }
    }

    override suspend fun stop(context: ModuleContext) {
        webServer?.stop()
        webServer = null
    }
}
