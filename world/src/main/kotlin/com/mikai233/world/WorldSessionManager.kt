package com.mikai233.world

import akka.actor.ActorRef
import com.google.protobuf.GeneratedMessage
import com.mikai233.common.conf.ServerMode
import com.mikai233.common.extension.invokeOnTargetMode
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tell
import com.mikai233.common.formatMessage
import com.mikai233.common.message.Message
import com.mikai233.common.message.ServerProtobuf

typealias PlayerSession = WorldSessionManager.WorldSession

class WorldSessionManager(val world: WorldActor) : Map<Long, WorldSessionManager.WorldSession> {
    private val sessions: MutableMap<Long, WorldSession> = mutableMapOf()

    inner class WorldSession(private val playerId: Long, private val channelActor: ActorRef) {
        private val logger = logger()

        fun sendChannel(message: Message) {
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

    fun send(playerId: Long, message: GeneratedMessage) {
        get(playerId)?.send(message)
    }

    fun <M : Message> sendRaw(playerId: Long, message: M) {
        get(playerId)?.sendChannel(message)
    }

    override val size: Int
        get() = sessions.size
    override val entries: Set<Map.Entry<Long, WorldSession>>
        get() = sessions.entries
    override val keys: Set<Long>
        get() = sessions.keys
    override val values: Collection<WorldSession>
        get() = sessions.values

    override fun containsKey(key: Long): Boolean {
        return sessions.containsKey(key)
    }

    override fun containsValue(value: WorldSession): Boolean {
        return sessions.containsValue(value)
    }

    override fun get(key: Long): WorldSession? {
        return sessions[key]
    }

    override fun isEmpty(): Boolean {
        return sessions.isEmpty()
    }
}
