package com.mikai233.gate

import com.mikai233.common.extension.logger
import io.github.realmlabs.asteria.gateway.GatewaySession
import org.apache.pekko.actor.ActorRef
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class GateConnectionDrainer {
    private val logger = logger()
    private val draining = AtomicBoolean(false)
    private val contextRef = AtomicReference<GateDrainContext?>(null)
    private val sessions = ConcurrentHashMap<String, GatewaySession>()

    val acceptingConnections: Boolean
        get() = !draining.get()

    val activeSessionCount: Int
        get() = sessions.size

    val drainContext: GateDrainContext?
        get() = contextRef.get()

    val activePlayerIds: Set<Long>
        get() = sessions.values.mapNotNullTo(linkedSetOf()) { session ->
            session.get(GatePlayerIdKey)
        }

    fun beginDrain(reason: String) {
        if (draining.compareAndSet(false, true)) {
            logger.info("gate connection drain started reason={} activeSessions={}", reason, activeSessionCount)
        }
    }

    fun beginDrain(planId: String, coordinator: ActorRef, reason: String) {
        contextRef.compareAndSet(null, GateDrainContext(planId, coordinator))
        beginDrain(reason)
    }

    fun register(session: GatewaySession): Boolean {
        if (draining.get()) {
            session.closeGateChannel()
            return false
        }
        sessions[session.id.value] = session
        if (draining.get()) {
            unregister(session)
            session.closeGateChannel()
            return false
        }
        return true
    }

    fun unregister(session: GatewaySession) {
        sessions.remove(session.id.value)
    }

    fun closeAll() {
        sessions.values.forEach { session ->
            runCatching {
                session.closeGateChannel()
            }.onFailure {
                logger.warn("failed to close gate session id={}", session.id.value, it)
            }
        }
        logger.info("gate connection drain close requested activeSessions={}", activeSessionCount)
    }
}

data class GateDrainContext(
    val planId: String,
    val coordinator: ActorRef,
)
