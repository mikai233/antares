package com.mikai233.common.inject

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent

data class XKoin(val koinApplication: KoinApplication) : KoinComponent {
    override fun getKoin(): Koin {
        return koinApplication.koin
    }
}
