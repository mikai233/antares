package com.mikai233.common.message.catalog

import kotlin.reflect.KClass

enum class CatalogDispatcherKind {
    PROTOBUF,
    INTERNAL,
}

data class MessageCatalogEntry(
    val messageClass: KClass<*>,
    val handlerClass: KClass<*>,
    val dispatcher: CatalogDispatcherKind,
)

interface MessageCatalog {
    val bindings: List<MessageCatalogEntry>

    val protobufBindings: List<MessageCatalogEntry>
        get() = bindings.filter { it.dispatcher == CatalogDispatcherKind.PROTOBUF }

    val internalBindings: List<MessageCatalogEntry>
        get() = bindings.filter { it.dispatcher == CatalogDispatcherKind.INTERNAL }
}
