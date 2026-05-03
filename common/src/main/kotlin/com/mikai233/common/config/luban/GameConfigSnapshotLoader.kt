package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigLoader
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTable
import io.github.realmlabs.asteria.config.DefaultConfigSnapshot

class GameConfigSnapshotLoader(
    private val delegate: ConfigLoader,
) : ConfigLoader {
    override suspend fun load(): ConfigSnapshot {
        val snapshot = delegate.load()
        val components = snapshot.components()
        return DefaultConfigSnapshot(
            revision = snapshot.revision,
            tables = components.filterIsInstance<ConfigTable<*, *>>(),
            components = components,
        )
    }
}
