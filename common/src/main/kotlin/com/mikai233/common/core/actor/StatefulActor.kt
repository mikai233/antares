package com.mikai233.common.core.actor

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Receive
import com.mikai233.common.annotation.AllOpen
import com.mikai233.common.msg.Message
import kotlin.reflect.KClass

enum class Stateful {
    Init,
    Up,
    Stop,
}

@AllOpen
abstract class StatefulActor<M, INIT, UP, STOP>(
    context: ActorContext<M>,
    val init: KClass<INIT>,
    val up: KClass<UP>,
    val stop: KClass<STOP>
) : AbstractBehavior<M>(context) where M : Message, M : Any, INIT : M, UP : M, STOP : M {

    override fun createReceive(): Receive<M> {
        TODO()
    }

    abstract fun onInitRecv(msg: INIT): Behavior<INIT>

    abstract fun onUpRecv(msg: UP): Behavior<UP>

    abstract fun onStopRecv(stop: STOP): Behavior<STOP>

}
