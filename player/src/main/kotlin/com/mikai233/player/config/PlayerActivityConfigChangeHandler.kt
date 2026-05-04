package com.mikai233.player.config

import com.mikai233.common.annotation.GameConfigChangeHandler
import com.mikai233.common.config.ConfigChangeHandler
import com.mikai233.common.config.configTables
import com.mikai233.common.config.luban.tbActivity
import com.mikai233.common.event.GameConfigChangedEvent
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActivityMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName

@GameConfigChangeHandler
class PlayerActivityConfigChangeHandler : ConfigChangeHandler<PlayerActor> {
    override val watchedTables: Set<ConfigTableName> = configTables("activities")

    private val logger = logger()

    override fun handle(actor: PlayerActor, event: GameConfigChangedEvent) {
        tryCatch(logger) {
            reconcile(actor, event.current)
        }
    }

    override fun catchUp(actor: PlayerActor, snapshot: ConfigSnapshot) {
        tryCatch(logger) {
            reconcile(actor, snapshot)
        }
    }

    private fun reconcile(actor: PlayerActor, snapshot: ConfigSnapshot) {
        val player = actor.manager.get<PlayerMem>().player
        actor.manager.get<PlayerActivityMem>().syncFromConfigs(
            playerLevel = player.level,
            activityConfigs = snapshot.tbActivity.all(),
        )
    }
}
