package com.mikai233.player.config

import com.mikai233.common.config.luban.GameConfigTables
import com.mikai233.common.config.luban.tbActivity
import com.mikai233.player.PlayerActor
import com.mikai233.player.data.PlayerActivityMem
import com.mikai233.player.data.PlayerMem
import io.github.realmlabs.asteria.config.ConfigChangeHandler
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.annotations.AsteriaConfigChangeHandler
import io.github.realmlabs.asteria.config.configTables

@AsteriaConfigChangeHandler
class PlayerActivityConfigChangeHandler : ConfigChangeHandler<PlayerActor> {
    override val watchedTables: Set<ConfigTableName> = configTables(GameConfigTables.TbActivity)

    override fun handle(receiver: PlayerActor, snapshot: ConfigSnapshot) {
        val player = receiver.manager.get<PlayerMem>().player
        receiver.manager.get<PlayerActivityMem>().syncFromConfigs(
            playerLevel = player.level,
            activityConfigs = snapshot.tbActivity.all(),
        )
    }
}
