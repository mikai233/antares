package com.mikai233.shared

import akka.actor.typed.receptionist.ServiceKey
import com.mikai233.shared.message.SerdeScriptMessage

fun scriptActorServiceKey(machineIp: String, port: Int): ServiceKey<SerdeScriptMessage> =
    ServiceKey.create(SerdeScriptMessage::class.java, "scriptActor@$machineIp:$port")