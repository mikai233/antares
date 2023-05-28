package com.mikai233.common.core

import com.mikai233.common.ext.logger
import com.mikai233.common.inject.XKoin
import org.koin.core.component.KoinComponent
import java.util.*

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2023/5/9
 */
open class Server(val koin: XKoin) : KoinComponent by koin {
    private val logger = logger()

    @Volatile
    var state: State = State.Uninitialized
        set(value) {
            val previousState = field
            field = value
            logger.info("state change from:{} to:{}", previousState, field)
            eventListeners[value]?.forEach {
                it(field)
            }
        }
    private val eventListeners: EnumMap<State, MutableList<(State) -> Unit>> = EnumMap(State::class.java)

    fun onInit() {

    }

    fun onStop() {
        getKoin().close()
    }
}