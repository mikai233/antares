package com.mikai233.common.core.components

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import com.mikai233.common.core.Server
import com.mikai233.common.msg.Message

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

interface ClusterMessage : Message

open class Cluster<T : ClusterMessage>(private val server: Server, private val behavior: Behavior<T>) : Component {
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

    }

    override fun shutdown() {

    }
}