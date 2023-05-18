package com.mikai233.stardust

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Launcher
import com.mikai233.common.core.components.Role
import com.mikai233.common.core.components.config.Node
import com.mikai233.common.core.components.config.nodePath
import com.mikai233.common.core.components.config.serverHostPath
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.buildSimpleZkClient
import com.mikai233.gate.GateNode
import com.mikai233.player.PlayerNode
import org.reflections.Reflections
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class StardustLauncher {
    private val curator = buildSimpleZkClient(GlobalEnv.zkConnect).apply {
        start()
    }
    private val roleNode: EnumMap<Role, KClass<out Launcher>> = EnumMap(Role::class.java)
    private val nodes: ArrayList<Launcher> = arrayListOf()
    private val clazzToCtor: Map<KClass<out Launcher>, KFunction<Launcher>>

    init {
        roleNode[Role.Gate] = GateNode::class
        roleNode[Role.Player] = PlayerNode::class
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
            roleNode[node.role]?.let { clazz ->
                clazzToCtor[clazz]?.let {
                    val nodeInstance = it.call(node.port, true)
                    nodeInstance.launch()
                    nodes.add(nodeInstance)
                }
            }
        }
    }
}