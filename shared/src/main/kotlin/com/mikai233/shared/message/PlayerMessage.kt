package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.shared.script.ActorScriptFunction
import com.mikai233.shared.script.Script

data class PlayerRunnable(private val block: () -> Unit) : Runnable, PlayerMessage {
    override fun run() {
        block()
    }
}

object StopPlayer : SerdePlayerMessage

data class PlayerScript(val script: Script) : SerdePlayerMessage

object PlayerInitDone : PlayerMessage

data class ExecutePlayerScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : PlayerMessage

data class PlayerProtobufEnvelope(val inner: GeneratedMessageV3, val channelActor: ActorRef<SerdeChannelMessage>) :
    BusinessPlayerMessage