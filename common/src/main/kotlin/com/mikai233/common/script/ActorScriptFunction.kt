package com.mikai233.common.script

import akka.actor.AbstractActor


interface ActorScriptFunction<T : AbstractActor> : Function2<T, ByteArray?, Unit>
