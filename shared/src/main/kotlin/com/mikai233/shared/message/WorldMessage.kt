package com.mikai233.shared.message

import akka.actor.typed.javadsl.AbstractBehavior
import com.mikai233.shared.script.ActorScriptFunction

data class ExecuteWorldScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : WorldMessage

data class WorldRunnable(private val block: () -> Unit) : Runnable, WorldMessage {
    override fun run() {
        block()
    }
}

object StopWorld : SerdeWorldMessage
