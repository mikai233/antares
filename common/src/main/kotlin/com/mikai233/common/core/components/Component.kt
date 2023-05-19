package com.mikai233.common.core.components

interface Component {
    fun init() = Unit

    fun shutdown() = Unit
}