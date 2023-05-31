package com.mikai233.common.entity

sealed interface Entity<K> where K : Any {
    fun key(): K
}