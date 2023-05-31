package com.mikai233.common.entity

interface RootEntity<K> : Entity {
    fun key(): K
}