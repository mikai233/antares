package com.mikai233.shared.message

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.shared.script.ActorScriptFunction

data class ExecuteWorldScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : WorldMessage

data class WorldRunnable(private val block: () -> Unit) : Runnable, WorldMessage {
    override fun run() {
        block()
    }
}

object StopWorld : SerdeWorldMessage

object WakeupGameWorld : SerdeWorldMessage

object WorldInitDone : WorldMessage

data class WorldProtobufEnvelope(val inner: GeneratedMessageV3) : BusinessWorldMessage

data class PlayerLogin(val req: LoginReq, val channelActor: ActorRef<SerdeChannelMessage>) : BusinessWorldMessage