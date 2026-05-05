package com.mikai233.common.config.luban.validation

import com.mikai233.common.config.luban.tbDroppool
import com.mikai233.common.config.luban.tbItem
import io.github.realmlabs.asteria.config.ConfigSnapshot

object DropPoolConfigValidator : GameConfigValidator {
    override fun validate(snapshot: ConfigSnapshot) {
        val itemIds = snapshot.tbItem.ids

        snapshot.tbDroppool.all().forEach { dropPool ->
            dropPool.entries.forEach { entry ->
                check(entry.itemId in itemIds) {
                    "drop pool ${dropPool.id} entry references missing item ${entry.itemId}"
                }
            }
        }
    }
}
