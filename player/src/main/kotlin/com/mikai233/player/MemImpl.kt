package com.mikai233.player

import com.mikai233.common.db.MemData
import com.mikai233.player.data.PlayerActionMem
import com.mikai233.player.data.PlayerMem
import kotlin.reflect.KClass

val MemImpl: Set<KClass<out MemData<*>>> = setOf(
    PlayerActionMem::class,
    PlayerMem::class,
)
