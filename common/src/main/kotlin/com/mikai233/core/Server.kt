package com.mikai233.core

import com.mikai233.core.components.Component
import com.mikai233.core.components.config.ZookeeperConfigCenter
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 */
open class Server {
    var state: State = State.Uninitialized
    val components: HashMap<KClass<out Component>, Component> = hashMapOf()
    val componentsOrder: MutableList<KClass<out Component>> = mutableListOf()

    inner class ComponentBuilder {
        inline fun <reified C : Component> component(block: Server.() -> C) {
            val key = C::class
            check(components.containsKey(key).not()) { "duplicate add component:${key}" }
            componentsOrder.add(key)
            val c = block()
            components[key] = c as Component
        }
    }

    inline fun <reified C : Component> component(): C {
        val key = C::class
        return requireNotNull(components[key]) { "component:${C::class} not found in components" } as C
    }

    fun components(block: ComponentBuilder.() -> Unit) {
        check(state == State.Uninitialized)
        val builder = ComponentBuilder()
        block.invoke(builder)
    }

    fun start() {
        check(state == State.Uninitialized)
        state = State.Initializing
        componentsOrder.mapNotNull(components::get).forEach { component ->
            component.init(this)
        }
    }

    fun stop() {
        check(state == State.Running)
        state = State.ShuttingDown
        componentsOrder.reversed().mapNotNull(components::get).forEach { component ->
            component.init(this)
        }
    }
}

fun main() {
    val s = Server()
    s.components {
        component { ZookeeperConfigCenter() }
    }
    s.start()
}