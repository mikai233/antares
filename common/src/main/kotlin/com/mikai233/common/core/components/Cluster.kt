package com.mikai233.common.core.components

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import com.mikai233.common.core.Server

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

interface ClusterMessage

open class Cluster<T : ClusterMessage>(private val behavior: Behavior<T>) : Component {
    private lateinit var server: Server
    lateinit var system: ActorSystem<T>
        private set
    private lateinit var nodeConfigsComponent: NodeConfigsComponent

    override fun init(server: Server) {
        this.server = server
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

    }

    override fun shutdown() {

    }
}