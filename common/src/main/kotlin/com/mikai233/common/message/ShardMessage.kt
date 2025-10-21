package com.mikai233.common.message

interface ShardMessage<T : Any> : Message {
    val id: T
}
