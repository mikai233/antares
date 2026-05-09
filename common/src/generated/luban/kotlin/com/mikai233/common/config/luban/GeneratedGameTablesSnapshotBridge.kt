package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.SnapshotEntry
import io.github.realmlabs.asteria.config.luban.LubanSnapshotBridge

object GameTablesSnapshotBridge : LubanSnapshotBridge<GameTables, GameTables.IByteBufLoader> {
    override val loaderType = GameTables.IByteBufLoader::class

    override fun createTables(loader: GameTables.IByteBufLoader): GameTables {
        return GameTables(loader)
    }

    override fun buildEntries(tables: GameTables): List<SnapshotEntry> {
        return buildList {
            addAll(buildGameTableSnapshotEntriesChunk0(tables))
        }
    }
}
