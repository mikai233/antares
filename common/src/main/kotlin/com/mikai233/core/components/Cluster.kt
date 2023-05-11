package com.mikai233.core.components

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import com.mikai233.core.Server
import com.mikai233.core.components.config.Node
import com.mikai233.core.components.config.ServerConfigsComponent
import com.mikai233.ext.logger

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */
enum class Role {
    Home,
    Gate,
    World,
    Global,
}

interface ClusterMessage

open class Cluster<T : ClusterMessage>(private val behavior: Behavior<T>) : Component {
    val logger = logger()
    lateinit var node: Node
        private set
    private lateinit var server: Server
    private lateinit var system: ActorSystem<T>
    private lateinit var serverConfigsComponent: ServerConfigsComponent

    override fun init(server: Server) {
        this.server = server
        serverConfigsComponent = server.component()
        startActorSystem()
        afterStartActorSystem()
    }

    private fun startActorSystem() {
        system = ActorSystem.create(behavior, serverConfigsComponent.akkaSystemName, serverConfigsComponent.retrieveAkkaConfig())
    }

    private fun afterStartActorSystem() {

    }

    override fun shutdown() {

    }
}