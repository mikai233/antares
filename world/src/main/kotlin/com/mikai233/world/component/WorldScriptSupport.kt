package com.mikai233.world.component

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.ext.syncAsk
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.message.ScriptMessage
import com.mikai233.world.SpawnScriptActorReq
import com.mikai233.world.SpawnScriptActorResp
import com.mikai233.world.WorldSystemMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorldScriptSupport(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<WorldSystemMessage> by inject()
    lateinit var localScriptActor: ActorRef<ScriptMessage>
        private set

    init {
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