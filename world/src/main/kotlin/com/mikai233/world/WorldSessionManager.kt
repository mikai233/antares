package com.mikai233.world

import akka.actor.typed.ActorRef
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.ext.invokeOnTargetMode
import com.mikai233.common.ext.logger
import com.mikai233.common.ext.protobufJsonPrinter
import com.mikai233.shared.message.ChannelProtobufEnvelope
import com.mikai233.shared.message.SerdeChannelMessage

class WorldSessionManager(val world: WorldActor) {
    private val logger = logger()
    private val sessions: LinkedHashMap<Long, WorldSession> = LinkedHashMap(1000)

    inner class WorldSession(private val playerId: Long, private val channelActor: ActorRef<SerdeChannelMessage>) {
        private val logger = logger()
        private val protobufPrinter = protobufJsonPrinter()
        val world = this@WorldSessionManager.world
        val worldId = world.worldId

        fun write(message: SerdeChannelMessage) {
            channelActor.tell(message)
            invokeOnTargetMode(setOf(ServerMode.DevMode)) {
                if (message is ChannelProtobufEnvelope) {
                    logger.info("player:{} {}", playerId, protobufPrinter.print(message.inner))
                } else {
                    logger.info("player:{} {}", playerId, message)
                }
            }
        }

        fun writeProtobuf(message: GeneratedMessageV3) {
            write(ChannelProtobufEnvelope(message))
        }
    }

    fun createOrUpdateSession(playerId: Long, channelActor: ActorRef<SerdeChannelMessage>): WorldSession {
        val session = WorldSession(playerId, channelActor)
        sessions[playerId] = session
        return session
    }
}
