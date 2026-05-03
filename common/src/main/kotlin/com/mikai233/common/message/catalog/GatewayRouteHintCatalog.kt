package com.mikai233.common.message.catalog

import kotlin.reflect.KClass

enum class GatewayRouteTarget {
    GATEWAY_LOCAL,
    PLAYER_ENTITY,
    WORLD_ENTITY,
}

enum class GatewayEntityIdSource {
    NONE,
    MESSAGE_FIELD,
    SESSION_PLAYER_ID,
    SESSION_WORLD_ID,
}

enum class GatewayInjectionSource {
    ROUTE_ENTITY_ID,
    SESSION_PLAYER_ID,
    SESSION_WORLD_ID,
    CLEAR,
}

data class GatewayFieldInjection(
    val field: String,
    val source: GatewayInjectionSource,
)

data class GatewayRouteHintEntry(
    val messageClass: KClass<*>,
    val handlerClass: KClass<*>,
    val target: GatewayRouteTarget,
    val entityIdSource: GatewayEntityIdSource,
    val entityIdField: String,
    val injections: List<GatewayFieldInjection>,
)

interface GatewayRouteHintCatalog {
    val routes: List<GatewayRouteHintEntry>
}
