package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import com.google.protobuf.GeneratedMessageV3

data class PlayerProtobufEnvelope(val message: GeneratedMessageV3) : SerdePlayerMessage

data class PlayerRunnable(private val block: () -> Unit) : Runnable, PlayerMessage {
    override fun run() {
        block()
    }
}

object StopPlayer : SerdePlayerMessage

data class PlayerLogin(val channelActor: ActorRef<SerdeChannelMessage>) : SerdePlayerMessage

object PlayerInitDone : PlayerMessage