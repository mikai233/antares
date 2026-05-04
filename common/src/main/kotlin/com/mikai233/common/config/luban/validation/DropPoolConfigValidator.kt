package com.mikai233.common.config.luban.validation

import com.mikai233.common.config.luban.GameTables
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.requireComponent

object DropPoolConfigValidator : GameConfigValidator {
    override fun validate(snapshot: ConfigSnapshot) {
        val tables = snapshot.requireComponent<GameTables>()
        val itemIds = tables.getTbItem().ids

        tables.getTbDroppool().all().forEach { dropPool ->
            dropPool.entries.forEach { entry ->
                check(entry.itemId in itemIds) {
                    "drop pool ${dropPool.id} entry references missing item ${entry.itemId}"
                }
            }
        }
    }
}
