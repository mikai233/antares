package com.mikai233.shared.message

import akka.actor.typed.javadsl.AbstractBehavior
import com.mikai233.shared.script.ActorScriptFunction

data class ExecuteWorldScript(val script: ActorScriptFunction<in AbstractBehavior<*>>) : WorldMessage
