package com.mikai233.common.db

import org.springframework.core.convert.converter.Converter

class DocumentToMapConverter : Converter<Map<String, Any>, Map<String, Any>> {
    override fun convert(source: Map<String, Any>): Map<String, Any> {
        val target = mutableMapOf<String, Any>()
        source.forEach { (key, value) ->
            val newKey = key.replace("#DOT#", ".").replace("#DOLLAR#", "$")
            target[newKey] = value
        }
        return target
    }
}