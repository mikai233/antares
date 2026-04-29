package com.mikai233.world

import com.mikai233.common.db.MemData
import com.mikai233.world.data.PlayerAbstractMem
import com.mikai233.world.data.WorldActionMem
import kotlin.reflect.KClass

val MemImpl: Set<KClass<out MemData<*>>> = setOf(
    PlayerAbstractMem::class,
    WorldActionMem::class,
)
