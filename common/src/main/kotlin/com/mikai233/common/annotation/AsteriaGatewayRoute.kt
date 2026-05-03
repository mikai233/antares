package com.mikai233.common.annotation

import com.mikai233.common.message.catalog.GatewayEntityIdSource
import com.mikai233.common.message.catalog.GatewayRouteTarget

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class AsteriaGatewayRoute(
    val target: GatewayRouteTarget,
    val entityIdSource: GatewayEntityIdSource = GatewayEntityIdSource.NONE,
    val entityIdField: String = "",
    val injectRouteEntityIdTo: Array<String> = [],
    val injectSessionPlayerIdTo: Array<String> = [],
    val injectSessionWorldIdTo: Array<String> = [],
    val clearFields: Array<String> = [],
)
