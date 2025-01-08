package com.mikai233.common.serde

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import com.esotericsoftware.kryo.util.Pool
import com.mikai233.common.extension.logger
import org.objenesis.strategy.StdInstantiatorStrategy
import kotlin.reflect.KClass

class KryoPool(val classes: Array<KClass<*>>) {
    val logger = logger()

    private val pool = object : Pool<Kryo>(true, true) {
        override fun create(): Kryo {
            val kryo = Kryo()
            kryo.isRegistrationRequired = true
            kryo.instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
            var id = 30
            classes.toSet().forEach {
                val nextId = ++id
                kryo.register(it.java, nextId)
                logger.debug("register class: {} -> {}", it, nextId)
            }
            return kryo
        }
    }

    init {
        //预先创建一些Kryo对象以供使用
        val kryoList = mutableListOf<Kryo>()
        repeat(100) {
            kryoList.add(pool.obtain())
        }
        kryoList.forEach {
            pool.free(it)
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