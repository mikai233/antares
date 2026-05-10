package com.mikai233.common.runtime

import org.apache.pekko.actor.AbstractActorWithStash
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.Terminated
import org.slf4j.LoggerFactory

data class RestartSingletonChild(
    val planId: String,
)

data class SingletonChildRestarted(
    val planId: String,
)

class SingletonChildSupervisorActor(
    private val childName: String,
    private val childProps: Props,
    private val childStopMessage: Any,
    private val handoffMessageType: Class<*>,
) : AbstractActorWithStash() {
    companion object {
        fun props(
            childName: String,
            childProps: Props,
            childStopMessage: Any,
            handoffMessageType: Class<*>,
        ): Props = Props.create(
            SingletonChildSupervisorActor::class.java,
            childName,
            childProps,
            childStopMessage,
            handoffMessageType,
        )
    }

    private data class PendingRestart(
        val planId: String,
        val replyTo: ActorRef,
        val child: ActorRef,
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private var child: ActorRef? = null
    private var pendingRestart: PendingRestart? = null
    private var handoffInProgress: Boolean = false

    override fun preStart() {
        super.preStart()
        child = startChild()
        logger.info("{} started childName={}", self, childName)
    }

    override fun postStop() {
        logger.info("{} stopped childName={}", self, childName)
        super.postStop()
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(RestartSingletonChild::class.java) { handleRestart(it) }
            .match(Terminated::class.java) { handleTerminated(it.actor) }
            .matchAny { handleMessage(it) }
            .build()
    }

    private fun handleMessage(message: Any) {
        if (handoffMessageType.isInstance(message)) {
            handleHandoff()
            return
        }
        forwardToChild(message)
    }

    private fun handleRestart(command: RestartSingletonChild) {
        if (pendingRestart != null) {
            stash()
            return
        }
        val currentChild = child
        if (currentChild == null) {
            child = startChild()
            sender.tell(SingletonChildRestarted(command.planId), self)
            unstashAll()
            return
        }
        pendingRestart = PendingRestart(command.planId, sender, currentChild)
        logger.info("stopping singleton child for restart childName={} planId={}", childName, command.planId)
        currentChild.tell(childStopMessage, self)
    }

    private fun handleHandoff() {
        if (sender != context.parent) {
            logger.warn("ignoring singleton handoff childName={} from non-manager sender={}", childName, sender)
            return
        }
        handoffInProgress = true
        val currentChild = child
        if (currentChild == null) {
            context.stop(self)
            return
        }
        logger.info("stopping singleton child for handoff childName={}", childName)
        currentChild.tell(childStopMessage, self)
    }

    private fun handleTerminated(terminatedActor: ActorRef) {
        if (terminatedActor != child) {
            return
        }
        child = null
        if (handoffInProgress) {
            context.stop(self)
            return
        }
        val restart = pendingRestart
        child = startChild()
        if (restart != null) {
            pendingRestart = null
            restart.replyTo.tell(SingletonChildRestarted(restart.planId), self)
            unstashAll()
            return
        }
        logger.warn("singleton child terminated unexpectedly; restarted childName={} child={}", childName, child)
        unstashAll()
    }

    private fun forwardToChild(message: Any) {
        val currentChild = child
        if (currentChild == null || pendingRestart != null) {
            stash()
            return
        }
        currentChild.forward(message, context)
    }

    private fun startChild(): ActorRef {
        val nextChild = context.actorOf(childProps, childName)
        context.watch(nextChild)
        logger.info("singleton child started childName={} child={}", childName, nextChild)
        return nextChild
    }
}
