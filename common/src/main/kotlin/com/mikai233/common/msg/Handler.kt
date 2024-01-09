package com.mikai233.common.msg

import akka.actor.typed.javadsl.AbstractBehavior

interface Handler<A : AbstractBehavior<in M>, M : Message> {
    fun handle(actor: A, msg: M)
}