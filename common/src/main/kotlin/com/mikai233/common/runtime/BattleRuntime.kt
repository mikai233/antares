package com.mikai233.common.runtime

import com.mikai233.common.battle.BattleSessionRegistry
import com.mikai233.common.battle.BattleControlClient
import io.github.realmlabs.asteria.core.NodeRuntime

val NodeRuntime.battleControlClient: BattleControlClient
    get() = services.get(BattleControlClient::class)

val NodeRuntime.battleSessionRegistry: BattleSessionRegistry
    get() = services.get(BattleSessionRegistry::class)
