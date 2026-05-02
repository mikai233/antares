package com.mikai233.gate

import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import io.github.mikai233.asteria.gateway.netty.NettyGatewayServerOptions
import io.github.mikai233.asteria.gateway.netty.NettyTcpGatewayServerTransport
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope

class GateMessageForwardModule : AsteriaModule {
    override val name: String = "gate-message-forward"

    override suspend fun start(context: ModuleContext) {
        thread { MessageForward }
    }
}

class GateGatewayTransportModule(
    private val node: GateNode,
) : AsteriaModule {
    override val name: String = "gate-gateway-transport"

    private var transport: NettyTcpGatewayServerTransport? = null

    override suspend fun start(context: ModuleContext) {
        val gatewayTransport = NettyTcpGatewayServerTransport(
            NettyGatewayServerOptions(
                host = node.nettyConfig.host,
                port = node.nettyConfig.port,
                maxFrameLength = 1024 * 100,
            ),
            scope = context.services.get(CoroutineScope::class),
            pipelineInstaller = GateNettyPipeline.installer(node.protocolCodec),
        )
        gatewayTransport.start(GateTransportHandler(node))
        transport = gatewayTransport
    }

    override suspend fun stop(context: ModuleContext) {
        transport?.stop()
        transport = null
    }
}
