package com.mikai233.common.db

import akka.actor.AbstractActor
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

abstract class DataManager<A>(memDataPackage: String) where A : AbstractActor {
    val managers: MutableMap<KClass<out MemData<*>>, MemData<*>> = mutableMapOf()

    init {
        Reflections(memDataPackage).getSubTypesOf(MemData::class.java).forEach {
            val kClass = it.kotlin
            val primaryConstructor = requireNotNull(kClass.primaryConstructor) { "primary constructor not found" }
            managers[kClass] = primaryConstructor.call()
        }
    }

    inline fun <reified T : MemData<*>> get(): T {
        return requireNotNull(managers[T::class]) { "manager:${T::class} not found" } as T
    }

    abstract fun init()
}
