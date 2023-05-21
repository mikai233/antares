package com.mikai233.gm.component

import akka.actor.typed.ActorRef
import com.mikai233.common.core.Server
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.core.component.Component
import com.mikai233.common.ext.syncAsk
import com.mikai233.gm.GmSystemMessage
import com.mikai233.gm.SpawnScriptActorReq
import com.mikai233.gm.SpawnScriptActorResp
import com.mikai233.shared.message.ScriptMessage

class ScriptSupport(private val server: Server) : Component {
    private lateinit var akkaSystem: AkkaSystem<GmSystemMessage>
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