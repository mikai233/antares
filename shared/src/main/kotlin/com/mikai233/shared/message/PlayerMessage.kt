package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import com.google.protobuf.GeneratedMessageV3

data class ClientToPlayerMessage(val message: GeneratedMessageV3, override var playerId: Long) : PlayerMessage

data class PlayerRunnableMessage(override var playerId: Long, private val block: () -> Unit) : Runnable,
    InternalPlayerMessage {
    override fun run() {
        block()
    }
}

object StopPlayer : PlayerMessage {
    override var playerId: Long = 0
}

data class PlayerLogin(override var playerId: Long, val channelActor: ActorRef<InternalChannelMessage>) :
    InternalPlayerMessage