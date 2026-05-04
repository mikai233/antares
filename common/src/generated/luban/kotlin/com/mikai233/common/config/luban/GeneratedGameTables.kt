package com.mikai233.common.config.luban

import com.mikai233.common.config.luban.gen.GameTablesGen
import java.io.IOException
import luban.ByteBuf

class GameTables(
    loader: IByteBufLoader,
) {
    private val delegate = GameTablesGen(
        GameTablesGen.IByteBufLoader { file -> loader.load(file) },
    )

    private val activityTable by lazy { TbActivity(delegate.getTbactivity()) }
    private val droppoolTable by lazy { TbDroppool(delegate.getTbdroppool()) }
    private val monsterTable by lazy { TbMonster(delegate.getTbmonster()) }
    private val itemTable by lazy { TbItem(delegate.getTbitem()) }
    private val sceneTable by lazy { TbScene(delegate.getTbscene()) }

    fun getTbActivity(): TbActivity = activityTable

    fun getTbDroppool(): TbDroppool = droppoolTable

    fun getTbMonster(): TbMonster = monsterTable

    fun getTbItem(): TbItem = itemTable

    fun getTbScene(): TbScene = sceneTable

    fun interface IByteBufLoader {
        @Throws(IOException::class)
        fun load(file: String): ByteBuf
    }
}

typealias ActivityRow = com.mikai233.common.config.luban.gen.game.activity
typealias DroppoolRow = com.mikai233.common.config.luban.gen.game.droppool
typealias MonsterRow = com.mikai233.common.config.luban.gen.game.monster
typealias ItemRow = com.mikai233.common.config.luban.gen.game.item
typealias SceneRow = com.mikai233.common.config.luban.gen.game.scene

class TbActivity(delegate: com.mikai233.common.config.luban.gen.game.Tbactivity) : GameMapConfigTable<String, ActivityRow>(
    name = "activities",
    keyType = String::class,
    rowType = ActivityRow::class,
    rows = delegate.getDataMap(),
)

class TbDroppool(delegate: com.mikai233.common.config.luban.gen.game.Tbdroppool) : GameMapConfigTable<Int, DroppoolRow>(
    name = "droppools",
    keyType = Int::class,
    rowType = DroppoolRow::class,
    rows = delegate.getDataMap(),
)

class TbMonster(delegate: com.mikai233.common.config.luban.gen.game.Tbmonster) : GameMapConfigTable<Int, MonsterRow>(
    name = "monsters",
    keyType = Int::class,
    rowType = MonsterRow::class,
    rows = delegate.getDataMap(),
)

class TbItem(delegate: com.mikai233.common.config.luban.gen.game.Tbitem) : GameMapConfigTable<Int, ItemRow>(
    name = "items",
    keyType = Int::class,
    rowType = ItemRow::class,
    rows = delegate.getDataMap(),
) {
    fun byType(type: Int): List<ItemRow> {
        return all().filter { row -> row.type == type }
    }
}

class TbScene(delegate: com.mikai233.common.config.luban.gen.game.Tbscene) : GameMapConfigTable<Int, SceneRow>(
    name = "scenes",
    keyType = Int::class,
    rowType = SceneRow::class,
    rows = delegate.getDataMap(),
)

