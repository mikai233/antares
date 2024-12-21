package com.mikai233.shared

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.ShardEntityType
import com.mikai233.common.extension.startShardingProxy
import com.mikai233.shared.message.PlayerMessageExtractor
import com.mikai233.shared.message.SerdePlayerMessage
import com.mikai233.shared.message.SerdeWorldMessage
import com.mikai233.shared.message.WorldMessageExtractor

fun ActorSystem<*>.startPlayerActorShardingProxy(): ActorRef<ShardingEnvelope<SerdePlayerMessage>> {
    return startShardingProxy(
        ShardEntityType.PlayerActor.name,
        Role.Player,
        PlayerMessageExtractor(PlayerShardNum)
    )
}

fun ActorSystem<*>.startWorldActorShardingProxy(): ActorRef<ShardingEnvelope<SerdeWorldMessage>> {
    return startShardingProxy(
        ShardEntityType.WorldActor.name,
        Role.World,
        WorldMessageExtractor(WorldShardNum)
    )
}
