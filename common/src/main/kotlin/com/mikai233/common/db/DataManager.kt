package com.mikai233.common.db

import akka.actor.typed.javadsl.AbstractBehavior
import com.mikai233.common.msg.Message
import org.reflections.Reflections
import kotlin.reflect.KClass

abstract class DataManager<A, M>(memDataPackage: String) where A : AbstractBehavior<M>, M : Message {
    val managers: MutableMap<KClass<out MemData<A, M, *>>, MemData<A, M, in Any>> =
        mutableMapOf()

    init {
        Reflections(memDataPackage).getSubTypesOf(MemData::class.java).forEach {
            @Suppress("UNCHECKED_CAST")
            val clazz = it.kotlin as KClass<out MemData<A, M, *>>
            val constructor = it.getConstructor()
            @Suppress("UNCHECKED_CAST")
            managers[clazz] = constructor.newInstance() as MemData<A, M, in Any>
        }
    }

    inline fun <reified T : MemData<A, M, *>> get(): T {
        return requireNotNull(managers[T::class]) { "manager:${T::class} not found" } as T
    }

    abstract fun loadAll()
    abstract fun loadComplete()
}
