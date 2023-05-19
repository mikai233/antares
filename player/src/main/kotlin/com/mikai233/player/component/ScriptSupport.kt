package com.mikai233.player.component

import akka.actor.typed.ActorRef
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.Component
import com.mikai233.common.ext.syncAsk
import com.mikai233.player.LocalScriptActorReq
import com.mikai233.player.LocalScriptActorResp
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.shared.message.ScriptMessage

class ScriptSupport(private val server: Server) : Component {
    private lateinit var akkaSystem: AkkaSystem<PlayerSystemMessage>
    lateinit var localScriptActor: ActorRef<ScriptMessage>
        private set

    override fun init() {
        akkaSystem = server.component()
        getLocalScriptActor()
    }

    private fun getLocalScriptActor() {
        val resp =
            syncAsk<LocalScriptActorReq, LocalScriptActorResp, _>(akkaSystem.system, akkaSystem.system.scheduler()) {
                LocalScriptActorReq(it)
            }
        localScriptActor = resp.scriptActor
    }
}