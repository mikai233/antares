package com.mikai233.world

import akka.actor.typed.ActorRef
import akka.actor.typed.pubsub.Topic
import com.google.protobuf.GeneratedMessageV3
import com.mikai233.common.ext.logger
import com.mikai233.shared.logMessage
import com.mikai233.shared.message.ChannelProtobufEnvelope
import com.mikai233.shared.message.ProtobufEnvelopeToAllWorldClient
import com.mikai233.shared.message.ProtobufEnvelopeToWorldClient
import com.mikai233.shared.message.SerdeChannelMessage

class WorldSessionManager(val world: WorldActor) {
    private val logger = logger()
    private val sessions: LinkedHashMap<Long, WorldSession> = LinkedHashMap(1000)

    inner class WorldSession(private val playerId: Long, private val channelActor: ActorRef<SerdeChannelMessage>) {
        private val logger = logger()
        val world = this@WorldSessionManager.world
        val worldId = world.worldId

        fun write(message: SerdeChannelMessage) {
            channelActor.tell(message)
            logMessage(logger, message) { "playerId:$playerId worldId:$worldId" }
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

    fun broadcastWorldClient(message: GeneratedMessageV3) {
        world.worldTopic.tell(Topic.publish(ProtobufEnvelopeToWorldClient(message)))
    }

    fun broadcastAllWorldClient(message: GeneratedMessageV3) {
        world.allWorldTopic.tell(Topic.publish(ProtobufEnvelopeToAllWorldClient(message)))
    }
}
