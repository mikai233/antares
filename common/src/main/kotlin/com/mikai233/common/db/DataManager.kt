package com.mikai233.common.db

import akka.actor.AbstractActor
import org.reflections.Reflections
import kotlin.reflect.KClass

abstract class DataManager<A>(memDataPackage: String) where A : AbstractActor {
    val managers: MutableMap<KClass<out MemData<A, *>>, MemData<A, in Any>> =
        mutableMapOf()

    init {
        Reflections(memDataPackage).getSubTypesOf(MemData::class.java).forEach {
            @Suppress("UNCHECKED_CAST")
            val clazz = it.kotlin as KClass<out MemData<A, *>>
            val constructor = it.getConstructor()
            @Suppress("UNCHECKED_CAST")
            managers[clazz] = constructor.newInstance() as MemData<A, in Any>
        }
    }

    inline fun <reified T : MemData<A, *>> get(): T {
        return requireNotNull(managers[T::class]) { "manager:${T::class} not found" } as T
    }

    abstract fun loadAll()
    abstract fun loadComplete()
}
