package com.mikai233.shared.message

import akka.cluster.sharding.ShardRegion
import kotlin.math.abs

class PlayerMessageExtractor(private val numberOfShards: Int) : ShardRegion.MessageExtractor {
    override fun entityId(message: Any?): String {
        return when (message) {
            is PlayerMessage -> message.playerId.toString()
            else -> error("Unknown message type: $message")
        }
    }

    override fun entityMessage(message: Any): Any {
        return message
    }

    override fun shardId(entityId: String): String {
        return (abs(entityId.toLong()) % numberOfShards).toString()
    }
}

class WorldMessageExtractor(private val numberOfShards: Int) : ShardRegion.MessageExtractor {
    override fun entityId(message: Any?): String {
        return when (message) {
            is WorldMessage -> message.worldId.toString()
            else -> error("Unknown message type: $message")
        }
    }

    override fun entityMessage(message: Any): Any {
        return message
    }

    override fun shardId(entityId: String): String {
        return (abs(entityId.toLong()) % numberOfShards).toString()
    }
}
