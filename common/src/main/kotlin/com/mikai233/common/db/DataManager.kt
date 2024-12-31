package com.mikai233.common.db

import akka.actor.AbstractActor
import kotlin.reflect.KClass

abstract class DataManager<A> where A : AbstractActor {
    val managers: MutableMap<KClass<out MemData<*>>, MemData<*>> = mutableMapOf()

    inline fun <reified T : MemData<*>> get(): T {
        return requireNotNull(managers[T::class]) { "manager:${T::class} not found" } as T
    }

    abstract fun init()
}
