package com.mikai233.world

import akka.actor.ActorRef
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.shared.logMessage
import com.mikai233.shared.message.ChannelMessage
import com.mikai233.shared.message.ChannelProtobufEnvelope

class WorldSessionManager(val world: WorldActor) {
    private val sessions: MutableMap<Long, WorldSession> = mutableMapOf()

    inner class WorldSession(private val playerId: Long, private val channelActor: ActorRef) {
        private val logger = logger()

        fun sendChannel(message: ChannelMessage) {
            channelActor.tell(message)
            logMessage(logger, message) { "playerId:$playerId worldId:${world.worldId}" }
        }

        fun send(message: GeneratedMessage) {
            sendChannel(ChannelProtobufEnvelope(message))
        }
    }

    fun createOrUpdateSession(playerId: Long, channelActor: ActorRef): WorldSession {
        val session = WorldSession(playerId, channelActor)
        sessions[playerId] = session
        return session
    }

    fun broadcastWorldClient(message: GeneratedMessage) {
        //TODO
    }

    fun broadcastAllWorldClient(message: GeneratedMessage) {
        //TODO
    }
}
