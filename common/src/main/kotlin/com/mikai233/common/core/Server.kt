package com.mikai233.common.core

import com.mikai233.common.core.components.Component
import com.mikai233.common.ext.logger
import java.util.*
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 */
open class Server {
    private val logger = logger()

    @Volatile
    var state: State = State.Uninitialized
        set(value) {
            val previousState = field
            field = value
            logger.info("state change from:{} to:{}", previousState, field)
            eventListeners[value]?.forEach {
                it(field)
            }
        }
    val components: HashMap<KClass<out Component>, Component> = hashMapOf()
    val componentsOrder: MutableList<KClass<out Component>> = mutableListOf()
    private val eventListeners: EnumMap<State, MutableList<(State) -> Unit>> = EnumMap(State::class.java)

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

    fun components(block: Server.ComponentBuilder.() -> Unit) {
        check(state == State.Uninitialized)
        val builder = ComponentBuilder()
        block.invoke(builder)
    }

    fun initComponents() {
        componentsOrder.mapNotNull(components::get).forEach(Component::init)
    }

    fun shutdownComponents() {
        componentsOrder.reversed().mapNotNull(components::get).forEach(Component::shutdown)
    }
}