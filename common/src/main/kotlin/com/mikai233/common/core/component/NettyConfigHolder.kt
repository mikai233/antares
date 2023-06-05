package com.mikai233.common.core.component

import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.NettyConfig
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.core.component.config.serverNetty
import com.mikai233.common.inject.XKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NettyConfigHolder(private val koin: XKoin) : KoinComponent by koin {
    private val configCenter: ZookeeperConfigCenter by inject()
    private lateinit var nettyConfig: NettyConfig

    init {
        initNettyConfig()
    }

    private fun initNettyConfig() {
        nettyConfig = configCenter.getConfigEx(serverNetty(GlobalEnv.machineIp))
    }
}
