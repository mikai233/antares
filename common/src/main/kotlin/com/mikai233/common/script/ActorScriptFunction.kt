package com.mikai233.common.script

import org.apache.pekko.actor.AbstractActor


interface ActorScriptFunction<T : AbstractActor> : Function2<T, ByteArray?, Unit>
