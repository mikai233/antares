package com.mikai233.shared.message

interface ShardMessage<T : Any> {
    val id: T
}