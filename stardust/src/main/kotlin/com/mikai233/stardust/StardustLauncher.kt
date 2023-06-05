package com.mikai233.stardust

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.component.Role
import com.mikai233.common.core.component.config.Node
import com.mikai233.common.core.component.config.nodePath
import com.mikai233.common.core.component.config.serverHostPath
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.buildSimpleZkClient
import com.mikai233.common.ext.logger
import com.mikai233.gate.GateNode
import com.mikai233.gm.GmNode
import com.mikai233.player.PlayerNode
import com.mikai233.world.WorldNode
import org.reflections.Reflections
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class StardustLauncher {
    private val logger = logger()
    private val curator = buildSimpleZkClient(GlobalEnv.zkConnect).apply {
        start()
    }
    private val roleNode: EnumMap<Role, KClass<out Launcher>> = EnumMap(Role::class.java)
    private val nodes: ArrayList<Launcher> = arrayListOf()
    private val clazzToCtor: Map<KClass<out Launcher>, KFunction<Launcher>>

    init {
        roleNode[Role.Gate] = GateNode::class
        roleNode[Role.Player] = PlayerNode::class
        roleNode[Role.World] = WorldNode::class
        roleNode[Role.Gm] = GmNode::class
        clazzToCtor = Reflections("com.mikai233").getSubTypesOf(Launcher::class.java).map {
            val clazz = it.kotlin
            val ctor = clazz.constructors.first()
            clazz to ctor
        }.associate { it }
    }


    fun launch() {
        val hostFullPath = serverHostPath(GlobalEnv.machineIp)
        val nodeConfig = mutableListOf<Node>()
        with(curator) {
            children.forPath(hostFullPath).forEach {
                val dataBytes = data.forPath(nodePath(GlobalEnv.machineIp, it))
                val node = Json.fromJson<Node>(dataBytes)
                nodeConfig.add(node)
            }
        }
        nodeConfig.sortByDescending { it.seed }
        nodeConfig.forEach { node ->
            logger.info("launch node:{}", node)
            val nodeClass = roleNode[node.role]
            if (nodeClass != null) {
                val nodeConstructor = requireNotNull(clazzToCtor[nodeClass]) { "node $nodeClass constructor not found" }
                val nodeInstance = nodeConstructor.call(node.port, true)
                nodeInstance.launch()
                nodes.add(nodeInstance)
            } else {
                logger.error("node class of role:{} not register", node.role)
            }
        }
    }
}
