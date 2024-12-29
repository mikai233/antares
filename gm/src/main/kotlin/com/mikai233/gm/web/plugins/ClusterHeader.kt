package com.mikai233.gm.web.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MissingClusterHeaderException : RuntimeException("没有选择集群")

@OptIn(ExperimentalEncodingApi::class)
fun Application.configureClusterHeader() {
    intercept(ApplicationCallPipeline.Plugins) {
        val clusterHeader = call.request.header(ClusterKey.name)
        if (clusterHeader != null) {
            val decodedHeader = Base64.decode(clusterHeader)
            call.attributes.put(ClusterKey, decodedHeader.decodeToString())
        }
    }
}

val ClusterKey = AttributeKey<String>("Cluster")