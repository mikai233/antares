package com.mikai233.world

import akka.actor.ActorRef
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.formatMessage
import com.mikai233.common.message.ChannelMessage
import com.mikai233.common.message.ServerProtobuf

class WorldSessionManager(val world: WorldActor) {
    private val sessions: MutableMap<Long, WorldSession> = mutableMapOf()

    inner class WorldSession(private val playerId: Long, private val channelActor: ActorRef) {
        private val logger = logger()

        private fun sendChannel(message: ChannelMessage) {
            channelActor.tell(message)
            invokeOnTargetMode(ServerMode.DevMode) {
                val formattedMessage = formatMessage(message)
                logger.debug("send message:{} to playerId:{} worldId:{}", formattedMessage, playerId, world.worldId)
            }
        }

        fun send(message: GeneratedMessage) {
            sendChannel(ServerProtobuf(message))
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
