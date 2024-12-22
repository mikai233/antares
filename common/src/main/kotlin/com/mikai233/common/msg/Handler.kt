package com.mikai233.common.msg

import akka.actor.AbstractActor

interface Handler<A : AbstractActor, M : Message> {
    fun handle(actor: A, msg: M)
}