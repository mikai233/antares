package com.mikai233.common.config

import io.github.realmlabs.asteria.config.ConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName

interface ConfigChangeHandler<A : Any> {
    val watchedTables: Set<ConfigTableName>

    fun handle(actor: A, event: ConfigChangedEvent)

    fun catchUp(actor: A, snapshot: ConfigSnapshot) = Unit
}

class ConfigChangeDispatcher<A : Any>(
    private val handlers: List<ConfigChangeHandler<A>> = emptyList(),
) {
    fun dispatch(actor: A, event: ConfigChangedEvent) {
        for (handler in handlers) {
            if (event.changedTables.any(handler.watchedTables::contains)) {
                handler.handle(actor, event)
            }
        }
    }

    fun catchUp(actor: A, snapshot: ConfigSnapshot) {
        for (handler in handlers) {
            handler.catchUp(actor, snapshot)
        }
    }
}

fun configTable(name: String): ConfigTableName = ConfigTableName(name)

fun configTables(vararg names: String): Set<ConfigTableName> {
    return names.mapTo(linkedSetOf(), ::configTable)
}
