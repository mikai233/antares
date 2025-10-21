package com.mikai233.common.message

import akka.cluster.sharding.ShardRegion
import com.mikai233.common.PLAYER_SHARD_NUM
import com.mikai233.common.WORLD_SHARD_NUM
import kotlin.math.abs

class LongShardMessageExtractor(private val numberOfShards: Int) : ShardRegion.MessageExtractor {
    override fun entityId(message: Any): String {
        return when (message) {
            is ShardMessage<*> -> message.id.toString()
            else -> error("Unknown message type: $message")
        }
    }

    override fun entityMessage(message: Any): Any {
        return message
    }

    override fun shardId(message: Any): String {
        return when (message) {
            is ShardMessage<*> -> (abs(message.id as Long) % numberOfShards).toString()
            else -> error("Unknown message type: $message")
        }
    }
}

val PlayerMessageExtractor = LongShardMessageExtractor(PLAYER_SHARD_NUM)

val WorldMessageExtractor = LongShardMessageExtractor(WORLD_SHARD_NUM)
