package com.mikai233.common.config.luban

import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.ListConfigTable
import io.github.realmlabs.asteria.config.OrderedMapConfigTable
import io.github.realmlabs.asteria.config.SingleConfigTable

class TbRotationMessage(delegate: com.mikai233.common.config.luban.gen.game.TbRotationMessage) :
    ListConfigTable<RotationMessageRow>(
        name = ConfigTableName("rotation_messages"),
        rowType = RotationMessageRow::class,
        rows = delegate.dataList,
    )

class TbGameGlobal(delegate: com.mikai233.common.config.luban.gen.game.TbGameGlobal) : SingleConfigTable<GameGlobalRow>(
    name = ConfigTableName("game_globals"),
    rowType = GameGlobalRow::class,
    row = delegate.data(),
)

class TbActivity(delegate: com.mikai233.common.config.luban.gen.game.Tbactivity) :
    OrderedMapConfigTable<String, ActivityRow>(
        name = ConfigTableName("activities"),
        keyType = String::class,
        rowType = ActivityRow::class,
        rows = delegate.dataList.map { row -> row.id to row },
    )

class TbDroppool(delegate: com.mikai233.common.config.luban.gen.game.Tbdroppool) :
    OrderedMapConfigTable<Int, DroppoolRow>(
        name = ConfigTableName("droppools"),
        keyType = Int::class,
        rowType = DroppoolRow::class,
        rows = delegate.dataList.map { row -> row.id to row },
    )

class TbMonster(delegate: com.mikai233.common.config.luban.gen.game.Tbmonster) : OrderedMapConfigTable<Int, MonsterRow>(
    name = ConfigTableName("monsters"),
    keyType = Int::class,
    rowType = MonsterRow::class,
    rows = delegate.dataList.map { row -> row.id to row },
)

class TbItem(delegate: com.mikai233.common.config.luban.gen.game.Tbitem) : OrderedMapConfigTable<Int, ItemRow>(
    name = ConfigTableName("items"),
    keyType = Int::class,
    rowType = ItemRow::class,
    rows = delegate.dataList.map { row -> row.id to row },
)

class TbScene(delegate: com.mikai233.common.config.luban.gen.game.Tbscene) : OrderedMapConfigTable<Int, SceneRow>(
    name = ConfigTableName("scenes"),
    keyType = Int::class,
    rowType = SceneRow::class,
    rows = delegate.dataList.map { row -> row.id to row },
)

