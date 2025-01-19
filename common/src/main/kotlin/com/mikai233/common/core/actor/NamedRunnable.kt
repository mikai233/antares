package com.mikai233.common.core.actor

import com.mikai233.common.annotation.Local
import com.mikai233.common.message.Message

@Local
data class NamedRunnable(
    val name: String,
    val block: () -> Unit,
) : Runnable, Message {
    override fun run() {
        block()
    }
}
