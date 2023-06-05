package com.mikai233.common.db

import org.springframework.data.mongodb.core.query.Query
import kotlin.reflect.KClass

/**
 * @param path root mode is null, field mode is not null
 */
data class TKey(val root: KClass<*>, val query: Query, val path: String?, val type: TType) {
    override fun toString(): String {
        return "TKey(root=$root, path=$path, type=$type)"
    }
}
