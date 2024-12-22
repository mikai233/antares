package com.mikai233.common.core.component

import akka.Done
import akka.actor.CoordinatedShutdown
import com.mikai233.common.core.Node
import com.mikai233.common.core.State
import com.mikai233.common.inject.XKoin
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
    Gm,
}

enum class ShardEntityType {
    PlayerActor,
    WorldActor,
}

interface GuardianMessage : Message

open class AkkaSystem<T : GuardianMessage>(private val koin: XKoin, private val behavior: Behavior<T>) :
    KoinComponent by koin {
    lateinit var system: ActorSystem<T>
        private set
    private val node: Node by inject()
    private val nodeConfigHolder: NodeConfigHolder by inject()

    init {
        startActorSystem()
        afterStartActorSystem()
    }

    private fun startActorSystem() {
        system = ActorSystem.create(
            behavior,
            nodeConfigHolder.akkaSystemName,
            nodeConfigHolder.retrieveAkkaConfig()
        )
    }

    private fun afterStartActorSystem() {
        addCoordinatedShutdownTasks()
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "change-server-state-stopping", taskSupplier {
                node.state = State.Stopping
            })
            addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(), "stop-services", taskSupplier {
                node.onStop()
            })
            addTask(CoordinatedShutdown.PhaseActorSystemTerminate(), "change-server-state-stopped", taskSupplier {
                node.state = State.Stopped
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
}
