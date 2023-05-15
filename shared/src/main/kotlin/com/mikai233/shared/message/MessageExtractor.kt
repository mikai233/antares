package com.mikai233.shared.message

import akka.cluster.sharding.typed.ShardingMessageExtractor
import kotlin.math.abs

class PlayerMessageExtractor(private val numberOfShards: Int) :
    ShardingMessageExtractor<PlayerMessage, PlayerMessage>() {
    override fun entityId(message: PlayerMessage): String {
        return message.playerId.toString()
    }

    override fun shardId(entityId: String): String {
        return (abs(entityId.hashCode()) % numberOfShards).toString()
    }

    override fun unwrapMessage(message: PlayerMessage): PlayerMessage {
        return message
    }
}