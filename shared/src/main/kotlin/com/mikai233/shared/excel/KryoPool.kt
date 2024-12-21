package com.mikai233.shared.excel

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.Pool
import kotlin.reflect.KClass

class KryoPool(val classes: Array<KClass<*>>) {
    private val pool = object : Pool<Kryo>(true, true) {
        override fun create(): Kryo {
            val kryo = Kryo()
            kryo.isRegistrationRequired = true
            var id = 30
            classes.forEach {
                kryo.register(it.java, ++id)
            }
            return kryo
        }
    }

    fun <R> use(block: Kryo.() -> R): R {
        val kryo = pool.obtain()
        return try {
            block(kryo)
        } finally {
            pool.free(kryo)
        }
    }
}