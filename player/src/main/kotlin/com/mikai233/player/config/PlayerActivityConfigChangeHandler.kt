package com.mikai233.player.config

import com.mikai233.common.config.ConfigChangeHandler
import com.mikai233.common.config.configTables
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.extension.logger
import com.mikai233.common.extension.tryCatch
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActivityMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.config.ConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.requireComponent

class PlayerActivityConfigChangeHandler : ConfigChangeHandler<PlayerActor> {
    override val watchedTables: Set<ConfigTableName> = configTables("activities")

    private val logger = logger()

    override fun handle(actor: PlayerActor, event: ConfigChangedEvent) {
        tryCatch(logger) {
            reconcile(actor, event.current.requireComponent())
        }
    }

    override fun catchUp(actor: PlayerActor, snapshot: ConfigSnapshot) {
        tryCatch(logger) {
            reconcile(actor, snapshot.requireComponent())
        }
    }

    private fun reconcile(actor: PlayerActor, tables: GameTables) {
        val player = actor.manager.get<PlayerMem>().player
        actor.manager.get<PlayerActivityMem>().syncFromConfigs(
            playerLevel = player.level,
            activityConfigs = tables.getTbActivity().all(),
        )
    }
}
