package com.mikai233.player.component

import akka.actor.typed.ActorRef
import com.mikai233.common.core.Server
import com.mikai233.common.core.components.AkkaSystem
import com.mikai233.common.core.components.Component
import com.mikai233.common.ext.syncAsk
import com.mikai233.player.PlayerSystemMessage
import com.mikai233.player.SpawnScriptActorReq
import com.mikai233.player.SpawnScriptActorResp
import com.mikai233.shared.message.ScriptMessage

class PlayerScriptSupport(private val server: Server) : Component {
    private lateinit var akkaSystem: AkkaSystem<PlayerSystemMessage>
    lateinit var localScriptActor: ActorRef<ScriptMessage>
        private set

    override fun init() {
        akkaSystem = server.component()
        getLocalScriptActor()
    }

    private fun getLocalScriptActor() {
        val resp =
            syncAsk<SpawnScriptActorReq, SpawnScriptActorResp, _>(akkaSystem.system, akkaSystem.system.scheduler()) {
                SpawnScriptActorReq(it)
            }
        localScriptActor = resp.scriptActor
    }
}