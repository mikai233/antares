package com.mikai233.common.core.component

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.Server
import com.mikai233.common.core.component.config.NettyConfig
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.core.component.config.serverNetty

class NettyConfigComponent(private val server: Server) : Component {
    private lateinit var configCenter: ZookeeperConfigCenter
    private lateinit var nettyConfig: NettyConfig
    override fun init() {
        configCenter = server.component()
        initNettyConfig()
    }

    private fun initNettyConfig() {
        nettyConfig = configCenter.getConfigEx(serverNetty(GlobalEnv.machineIp))
    }

    override fun shutdown() = Unit
}