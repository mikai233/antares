package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.shared.script.ActorScriptFunction

data class PlayerProtobufEnvelope(val message: GeneratedMessageV3) : SerdePlayerMessage

data class PlayerRunnable(private val block: () -> Unit) : Runnable, PlayerMessage {
    override fun run() {
        block()
    }
}

object StopPlayer : SerdePlayerMessage

data class PlayerLogin(val channelActor: ActorRef<SerdeChannelMessage>) : SerdePlayerMessage

object PlayerInitDone : PlayerMessage

data class ExecutePlayerScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : PlayerMessage