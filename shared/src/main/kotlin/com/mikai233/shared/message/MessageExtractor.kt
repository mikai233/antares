package com.mikai233.shared.message

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.ShardingMessageExtractor
import kotlin.math.abs

class PlayerMessageExtractor(private val numberOfShards: Int) :
    ShardingMessageExtractor<ShardingEnvelope<out PlayerMessage>, PlayerMessage>() {
    override fun entityId(message: ShardingEnvelope<out PlayerMessage>): String {
        return message.entityId()
    }

    override fun shardId(entityId: String): String {
        return (abs(entityId.hashCode()) % numberOfShards).toString()
    }

    override fun unwrapMessage(message: ShardingEnvelope<out PlayerMessage>): PlayerMessage {
        return message.message()
    }
}