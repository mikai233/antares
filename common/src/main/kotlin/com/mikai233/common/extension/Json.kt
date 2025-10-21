package com.mikai233.common.extension

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.reflect.KClass

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */

object Json {
    val mapper = jsonMapper {
        addModule(kotlinModule())
        addModule(GuavaModule())
        addModule(JavaTimeModule())
        addModule(Jdk8Module())
    }

    inline fun <reified T> fromStr(content: String): T {
        return mapper.readValue(content)
    }

    fun <T> fromStr(content: String, clazz: KClass<T>): T where T : Any {
        return mapper.readValue(content, clazz.java)
    }

    inline fun <reified T> fromBytes(src: ByteArray): T {
        return mapper.readValue(src)
    }

    fun <T> fromBytes(src: ByteArray, clazz: KClass<T>): T where T : Any {
        return mapper.readValue(src, clazz.java)
    }

    inline fun <reified T> toStr(value: T, pretty: Boolean = false): String {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
        } else {
            mapper.writeValueAsString(value)
        }
    }

    fun toBytes(value: Any, pretty: Boolean = false): ByteArray {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
        } else {
            mapper.writeValueAsBytes(value)
        }
    }

    inline fun <reified T> copy(value: T): T where T : Any {
        return fromBytes(toBytes(value))
    }
}
