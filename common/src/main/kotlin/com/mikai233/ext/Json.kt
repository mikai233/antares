package com.mikai233.ext

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/10
 */

object Json {
    val mapper = jacksonObjectMapper()

    inline fun <reified T> fromJson(content: String): T {
        return mapper.readValue(content, T::class.java)
    }

    inline fun <reified T> fromJson(src: ByteArray): T {
        return mapper.readValue(src, T::class.java)
    }

    inline fun <reified T> toJsonString(value: T, pretty: Boolean = false): String {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
        } else {
            mapper.writeValueAsString(value)
        }
    }

    inline fun <reified T> toJsonBytes(value: T, pretty: Boolean = false): ByteArray {
        return if (pretty) {
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
        } else {
            mapper.writeValueAsBytes(value)
        }
    }

    inline fun <reified T> deepCopy(value: T): T {
        return fromJson(toJsonBytes(value))
    }
}