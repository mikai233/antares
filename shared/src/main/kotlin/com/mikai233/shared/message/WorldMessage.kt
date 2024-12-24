package com.mikai233.shared.message

import akka.actor.AbstractActor
import akka.actor.ActorRef
import com.google.protobuf.GeneratedMessage
import com.mikai233.protocol.ProtoLogin.LoginReq
import com.mikai233.common.script.ActorScriptFunction

data class ExecuteWorldScript(val script: ActorScriptFunction<in AbstractActor>) : WorldMessage

object StopWorld : SerdeWorldMessage

object WakeupGameWorld : SerdeWorldMessage

object WorldInitDone : WorldMessage

object WorldTick : WorldMessage

data class WorldProtobufEnvelope(val message: GeneratedMessage) : BusinessWorldMessage

data class PlayerLogin(val req: LoginReq, val channelActor: ActorRef) : BusinessWorldMessage
