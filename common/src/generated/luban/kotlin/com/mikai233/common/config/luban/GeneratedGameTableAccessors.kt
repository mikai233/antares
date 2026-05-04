package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.table

val ConfigSnapshot.tbRotationMessage: TbRotationMessage
    get() = table()

val ConfigSnapshot.tbGameGlobal: TbGameGlobal
    get() = table()

val ConfigSnapshot.tbActivity: TbActivity
    get() = table()

val ConfigSnapshot.tbDroppool: TbDroppool
    get() = table()

val ConfigSnapshot.tbMonster: TbMonster
    get() = table()

val ConfigSnapshot.tbItem: TbItem
    get() = table()

val ConfigSnapshot.tbScene: TbScene
    get() = table()

