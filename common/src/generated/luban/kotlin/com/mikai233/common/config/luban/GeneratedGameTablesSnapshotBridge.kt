package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.SnapshotEntry
import io.github.realmlabs.asteria.config.listConfigTable
import io.github.realmlabs.asteria.config.luban.LubanSnapshotBridge
import io.github.realmlabs.asteria.config.orderedMapConfigTable
import io.github.realmlabs.asteria.config.singleConfigTable

object GameTablesSnapshotBridge : LubanSnapshotBridge<GameTables, GameTables.IByteBufLoader> {
    override val loaderType = GameTables.IByteBufLoader::class

    override fun createTables(loader: GameTables.IByteBufLoader): GameTables {
        return GameTables(loader)
    }

    override fun buildEntries(tables: GameTables): List<SnapshotEntry> {
        return listOf(
            SnapshotEntry.Table(listConfigTable(GameConfigTables.TbRotationMessage, tables.delegate.tbRotationMessage.dataList)),
            SnapshotEntry.Table(singleConfigTable(GameConfigTables.TbGameGlobal, tables.delegate.tbGameGlobal.data())),
            SnapshotEntry.Table(orderedMapConfigTable(GameConfigTables.TbActivity, tables.delegate.tbactivity.dataList.map { row -> row.id to row })),
            SnapshotEntry.Table(orderedMapConfigTable(GameConfigTables.TbDroppool, tables.delegate.tbdroppool.dataList.map { row -> row.id to row })),
            SnapshotEntry.Table(orderedMapConfigTable(GameConfigTables.TbMonster, tables.delegate.tbmonster.dataList.map { row -> row.id to row })),
            SnapshotEntry.Table(orderedMapConfigTable(GameConfigTables.TbItem, tables.delegate.tbitem.dataList.map { row -> row.id to row })),
            SnapshotEntry.Table(orderedMapConfigTable(GameConfigTables.TbScene, tables.delegate.tbscene.dataList.map { row -> row.id to row })),
        )
    }
}
