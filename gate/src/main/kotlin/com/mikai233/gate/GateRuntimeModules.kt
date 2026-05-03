package com.mikai233.gate

import com.mikai233.common.config.NettyConfig
import com.mikai233.common.config.nettyConfigPath
import io.github.mikai233.asteria.config.center.RuntimeConfigRepository
import io.github.mikai233.asteria.core.AsteriaModule
import io.github.mikai233.asteria.core.ModuleContext
import io.github.mikai233.asteria.gateway.netty.NettyGatewayServerOptions
import io.github.mikai233.asteria.gateway.netty.NettyTcpGatewayServerTransport
import kotlinx.coroutines.CoroutineScope

class GateGatewayTransportModule(
    private val node: GateNode,
) : AsteriaModule {
    override val name: String = "gate-gateway-transport"

    private var transport: NettyTcpGatewayServerTransport? = null

    override suspend fun start(context: ModuleContext) {
        val repository = context.services.get(RuntimeConfigRepository::class)
        val config = repository.get<NettyConfig>(nettyConfigPath(node.nodeId))?.value
            ?: error("runtime config ${nettyConfigPath(node.nodeId)} not found")
        val gatewayTransport = NettyTcpGatewayServerTransport(
            NettyGatewayServerOptions(
                host = config.host,
                port = config.port,
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
