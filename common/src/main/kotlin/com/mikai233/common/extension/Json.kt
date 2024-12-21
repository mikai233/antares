package com.mikai233.common.extension

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */

object Json {
    val mapper = jsonMapper {
        addModule(kotlinModule())
        addModule(GuavaModule())
    }

    inline fun <reified T> fromStr(content: String): T {
        return mapper.readValue(content)
    }

    inline fun <reified T> fromBytes(src: ByteArray): T {
        return mapper.readValue(src)
    }

    inline fun <reified T> toStr(value: T, pretty: Boolean = false): String {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
        } else {
            mapper.writeValueAsString(value)
        }
    }

    inline fun <reified T> toBytes(value: T, pretty: Boolean = false): ByteArray {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
        } else {
            mapper.writeValueAsBytes(value)
        }
    }

    inline fun <reified T> copy(value: T): T {
        return fromBytes(toBytes(value))
    }
}
