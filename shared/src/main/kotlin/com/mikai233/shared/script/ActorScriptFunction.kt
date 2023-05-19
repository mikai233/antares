package com.mikai233.shared.script

import akka.actor.typed.javadsl.AbstractBehavior


interface ActorScriptFunction<T : AbstractBehavior<*>> : Function1<T, Unit>