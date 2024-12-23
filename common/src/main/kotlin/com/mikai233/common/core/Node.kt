package com.mikai233.common.core

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import com.mikai233.common.core.config.ConfigCache
import com.mikai233.common.core.config.TestConfig
import com.mikai233.common.extension.logger
import com.typesafe.config.Config
import kotlinx.coroutines.runBlocking
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 */
open class Node(val name: String, val config: Config, zookeeperConnectString: String) {
    val logger = logger()

    val system: ActorSystem = ActorSystem.create(name, config)

    private val zookeeper: AsyncCuratorFramework by lazy {
        val client = CuratorFrameworkFactory.newClient(
            zookeeperConnectString,
            ExponentialBackoffRetry(2000, 10, 60000)
        )
        client.start()
        AsyncCuratorFramework.wrap(client)
    }

    @Volatile
    private var state: State = State.Unstarted

    //TODO test
    val testConfigCache = ConfigCache(zookeeper, "/test", TestConfig::class)

    private suspend fun changeState(newState: State) {
        val previousState = state
        state = newState
        logger.info("{} state change from:{} to:{}", this::class.simpleName, previousState, newState)
        stateListeners[newState]?.forEach { listener ->
            listener()
        }
    }

    private val stateListeners: EnumMap<State, MutableList<suspend () -> Unit>> = EnumMap(State::class.java)

    fun addStateListener(state: State, listener: suspend () -> Unit) {
        stateListeners.computeIfAbsent(state) { mutableListOf() }.add(listener)
    }

    suspend fun start() {
        addCoordinatedShutdownTasks()
        changeState(State.Starting)
    }

    private fun addCoordinatedShutdownTasks() {
        with(CoordinatedShutdown.get(system)) {
            addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "change_state_stopping", taskSupplier {
                changeState(State.Stopping)
            })
            addTask(CoordinatedShutdown.PhaseActorSystemTerminate(), "change_state_stopped", taskSupplier {
                changeState(State.Stopped)
            })
        }
    }

    private fun taskSupplier(task: suspend () -> Unit): Supplier<CompletionStage<Done>> {
        return Supplier {
            CompletableFuture.supplyAsync {
                runBlocking { task.invoke() }
                Done.done()
            }
        }
    }
}
