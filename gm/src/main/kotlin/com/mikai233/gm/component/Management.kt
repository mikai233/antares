package com.mikai233.gm.component

import akka.management.javadsl.AkkaManagement
import com.mikai233.common.core.component.AkkaSystem
import com.mikai233.common.inject.XKoin
import com.mikai233.gm.GmSystemMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/6/18
 */
class Management(private val koin: XKoin) : KoinComponent by koin {
    private val akkaSystem: AkkaSystem<GmSystemMessage> by inject()

    init {
        initAkkaManagement()
    }

    private fun initAkkaManagement() {
        AkkaManagement.get(akkaSystem.system).start();
    }
}