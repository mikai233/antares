package com.mikai233.player.config

import com.mikai233.common.config.luban.tbActivity
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActivityMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.config.ConfigChangeHandler
import io.github.realmlabs.asteria.config.ConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.annotations.AsteriaConfigChangeHandler
import io.github.realmlabs.asteria.config.configTables

@AsteriaConfigChangeHandler
class PlayerActivityConfigChangeHandler : ConfigChangeHandler<PlayerActor> {
    override val watchedTables: Set<ConfigTableName> = configTables("activities")

    private val logger = logger()

    override fun handleChange(receiver: PlayerActor, event: ConfigChangedEvent) {
        tryCatch(logger) {
            reconcile(receiver, event.current)
        }
    }

    override fun catchUp(receiver: PlayerActor, snapshot: ConfigSnapshot) {
        tryCatch(logger) {
            reconcile(receiver, snapshot)
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
