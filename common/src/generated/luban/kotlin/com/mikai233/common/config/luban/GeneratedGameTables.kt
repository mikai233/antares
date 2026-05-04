package com.mikai233.common.config.luban

import com.mikai233.common.config.luban.gen.GameTablesGen
import io.github.realmlabs.asteria.config.ConfigSnapshot
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.ListConfigTable
import io.github.realmlabs.asteria.config.OrderedMapConfigTable
import io.github.realmlabs.asteria.config.SingleConfigTable
import io.github.realmlabs.asteria.config.table
import luban.ByteBuf
import java.io.IOException

class GameTables(
    loader: IByteBufLoader,
) {
    private val delegate = GameTablesGen { file -> loader.load(file) }

    val tbRotationMessage by lazy { TbRotationMessage(delegate.tbRotationMessage) }

    val tbGameGlobal by lazy { TbGameGlobal(delegate.tbGameGlobal) }

    val tbActivity by lazy { TbActivity(delegate.tbactivity) }

    val tbDroppool by lazy { TbDroppool(delegate.tbdroppool) }

    val tbMonster by lazy { TbMonster(delegate.tbmonster) }

    val tbItem by lazy { TbItem(delegate.tbitem) }

    val tbScene by lazy { TbScene(delegate.tbscene) }

    fun interface IByteBufLoader {
        @Throws(IOException::class)
        fun load(file: String): ByteBuf
    }
}

typealias RotationMessageRow = com.mikai233.common.config.luban.gen.game.RotationMessage
typealias GameGlobalRow = com.mikai233.common.config.luban.gen.game.GameGlobal
typealias ActivityRow = com.mikai233.common.config.luban.gen.game.activity
typealias DroppoolRow = com.mikai233.common.config.luban.gen.game.droppool
typealias MonsterRow = com.mikai233.common.config.luban.gen.game.monster
typealias ItemRow = com.mikai233.common.config.luban.gen.game.item
typealias SceneRow = com.mikai233.common.config.luban.gen.game.scene

class TbRotationMessage(delegate: com.mikai233.common.config.luban.gen.game.TbRotationMessage) : ListConfigTable<RotationMessageRow>(
    name = ConfigTableName("rotation_messages"),
    rowType = RotationMessageRow::class,
    rows = delegate.dataList,
)

class TbGameGlobal(delegate: com.mikai233.common.config.luban.gen.game.TbGameGlobal) : SingleConfigTable<GameGlobalRow>(
    name = ConfigTableName("game_globals"),
    rowType = GameGlobalRow::class,
    row = delegate.data(),
)

class TbActivity(delegate: com.mikai233.common.config.luban.gen.game.Tbactivity) : OrderedMapConfigTable<String, ActivityRow>(
    name = ConfigTableName("activities"),
    keyType = String::class,
    rowType = ActivityRow::class,
    rows = delegate.dataList.map { row -> row.id to row },
)

class TbDroppool(delegate: com.mikai233.common.config.luban.gen.game.Tbdroppool) : OrderedMapConfigTable<Int, DroppoolRow>(
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

val ConfigSnapshot.tbRotationMessage: TbRotationMessage
    get() = table()

val ConfigSnapshot.tbGameGlobal: TbGameGlobal
    get() = table()

val ConfigSnapshot.tbActivity: TbActivity
    get() = table()

val ConfigSnapshot.tbDroppool: TbDroppool
    get() = table()

val ConfigSnapshot.tbMonster: TbMonster
    get() = table()

val ConfigSnapshot.tbItem: TbItem
    get() = table()

val ConfigSnapshot.tbScene: TbScene
    get() = table()

