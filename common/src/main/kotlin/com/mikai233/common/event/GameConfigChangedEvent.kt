package com.mikai233.common.event

import io.github.realmlabs.asteria.config.ConfigChangedEvent
import io.github.realmlabs.asteria.config.ConfigRevision
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName

data class GameConfigChangedEvent(
    val previousRevision: ConfigRevision,
    val currentRevision: ConfigRevision,
    val current: ConfigSnapshot,
    val changedTables: Set<ConfigTableName>,
) : Event {
    companion object {
        fun from(event: ConfigChangedEvent): GameConfigChangedEvent {
            return GameConfigChangedEvent(
                previousRevision = event.previousRevision,
                currentRevision = event.currentRevision,
                current = event.current,
                changedTables = event.changedTables,
            )
        }
    }
}
