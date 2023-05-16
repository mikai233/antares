package com.mikai233.common.core.components

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import com.mikai233.common.core.Server
import com.mikai233.common.core.State
import com.mikai233.common.msg.Message
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */
enum class Role {
    Player,
    Gate,
    World,
    Global,
}

enum class ShardEntityType {
    PlayerActor,
    WorldActor,
}

interface GuardianMessage : Message

open class AkkaSystem<T : GuardianMessage>(private val server: Server, private val behavior: Behavior<T>) : Component {
    lateinit var system: ActorSystem<T>
        private set
    private lateinit var nodeConfigsComponent: NodeConfigsComponent

    override fun init() {
        nodeConfigsComponent = server.component()
        startActorSystem()
        afterStartActorSystem()
    }

    private fun startActorSystem() {
        system = ActorSystem.create(
            behavior,
            nodeConfigsComponent.akkaSystemName,
            nodeConfigsComponent.retrieveAkkaConfig()
        )
    }

    private fun afterStartActorSystem() {
        addCoordinatedShutdownTasks()
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "change-server-state-stopping", taskSupplier {
                server.state = State.Stopping
            })
            addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(), "stop-services", taskSupplier {
                server.shutdownComponents()
            })
            addTask(CoordinatedShutdown.PhaseActorSystemTerminate(), "change-server-state-stopped", taskSupplier {
                server.state = State.Stopped
            })
        }
    }

    private fun taskSupplier(task: () -> Unit): Supplier<CompletionStage<Done>> {
        return Supplier {
            CompletableFuture.supplyAsync {
                task.invoke()
                Done.done()
            }
        }
    }

    override fun shutdown() {

    }
}