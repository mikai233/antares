package com.mikai233.global.component

import akka.actor.typed.ActorRef
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.ext.syncAsk
import com.mikai233.common.inject.XKoin
import com.mikai233.global.GlobalSystemMessage
import com.mikai233.global.SpawnScriptActorReq
import com.mikai233.global.SpawnScriptActorResp
import com.mikai233.shared.message.ScriptMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GlobalScriptSupport(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<GlobalSystemMessage> by inject()
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
