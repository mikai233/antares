package com.mikai233.common.message

data class ActorNamedRunnable(
    val name: String,
    val block: () -> Unit
) : Runnable, Message {
    override fun run() {
        block()
    }
}