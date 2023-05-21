package com.mikai233.common.core.component

interface Component {
    fun init() = Unit

    fun shutdown() = Unit
}