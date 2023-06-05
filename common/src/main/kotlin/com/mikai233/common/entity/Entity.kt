package com.mikai233.common.entity

import com.fasterxml.jackson.annotation.JsonTypeInfo


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
sealed interface Entity<K> where K : Any {
    fun key(): K
}
