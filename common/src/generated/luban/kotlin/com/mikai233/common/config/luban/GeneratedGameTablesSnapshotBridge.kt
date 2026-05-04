package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.SnapshotEntry
import io.github.realmlabs.asteria.config.luban.LubanSnapshotBridge

object GameTablesSnapshotBridge : LubanSnapshotBridge<GameTables, GameTables.IByteBufLoader> {
    override val loaderType = GameTables.IByteBufLoader::class

    override fun createTables(loader: GameTables.IByteBufLoader): GameTables {
        return GameTables(loader)
    }

    override fun buildEntries(tables: GameTables): List<SnapshotEntry> {
        return listOf(
            SnapshotEntry.Table(TbRotationMessage(tables.delegate.tbRotationMessage), TbRotationMessage::class),
            SnapshotEntry.Table(TbGameGlobal(tables.delegate.tbGameGlobal), TbGameGlobal::class),
            SnapshotEntry.Table(TbActivity(tables.delegate.tbactivity), TbActivity::class),
            SnapshotEntry.Table(TbDroppool(tables.delegate.tbdroppool), TbDroppool::class),
            SnapshotEntry.Table(TbMonster(tables.delegate.tbmonster), TbMonster::class),
            SnapshotEntry.Table(TbItem(tables.delegate.tbitem), TbItem::class),
            SnapshotEntry.Table(TbScene(tables.delegate.tbscene), TbScene::class),
        )
    }
}

