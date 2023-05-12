package com.mikai233.common.core.actor

import akka.actor.NotInfluenceReceiveTimeout

data class NamedRunnable(val name: String, private val block: () -> Unit) : Runnable, NotInfluenceReceiveTimeout {
    override fun run() {
        block()
    }
}

data class RunnableWrap<R : Runnable>(private val block: R) : Runnable, NotInfluenceReceiveTimeout {
    override fun run() {
        block.run()
    }
}
