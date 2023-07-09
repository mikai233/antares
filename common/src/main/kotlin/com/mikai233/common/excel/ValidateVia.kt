package com.mikai233.common.excel


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class ValidateVia(val validator: String)
