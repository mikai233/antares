package com.mikai233.shared.message.world

import akka.actor.ActorRef
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.message.Message
import com.mikai233.protocol.ProtoLogin
import com.mikai233.shared.message.WorldMessage

data object HandoffWorld : Message

data class StopWorld(override val worldId: Long) : WorldMessage

data class WakeupWorld(override val worldId: Long) : WorldMessage

data object WorldTick : Message

data class WorldProtobufEnvelope(override val worldId: Long, val message: GeneratedMessage) : WorldMessage

data class PlayerLogin(override val worldId: Long, val req: ProtoLogin.LoginReq, val channelActor: ActorRef) :
    WorldMessage
