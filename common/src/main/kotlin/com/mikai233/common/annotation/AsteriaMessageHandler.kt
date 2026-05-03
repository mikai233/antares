package com.mikai233.common.annotation

import com.mikai233.common.message.catalog.CatalogDispatcherKind

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class AsteriaMessageHandler(
    val dispatcher: CatalogDispatcherKind,
)
