package com.mikai233.common.config.luban

import com.fasterxml.jackson.module.kotlin.readValue
import com.mikai233.common.extension.Json
import io.github.mikai233.asteria.config.ConfigTable
import io.github.mikai233.asteria.config.ConfigTableName
import kotlin.reflect.KClass

class GameTables(
    private val loader: GameConfigLoader,
) {
    private val itemTable by lazy { TbItem(loader.load("item_tbitem")) }
    private val monsterTable by lazy { TbMonster(loader.load("monster_tbmonster")) }
    private val dropPoolTable by lazy { TbDropPool(loader.load("drop_tbdroppool")) }
    private val sceneTable by lazy { TbScene(loader.load("scene_tbscene")) }
    private val activityTable by lazy { TbActivity(loader.load("activity_tbactivity")) }

    fun getTbItem(): TbItem = itemTable

    fun getTbMonster(): TbMonster = monsterTable

    fun getTbDropPool(): TbDropPool = dropPoolTable

    fun getTbScene(): TbScene = sceneTable

    fun getTbActivity(): TbActivity = activityTable

    fun interface GameConfigLoader {
        fun load(file: String): GameConfigByteBuf
    }
}

class GameConfigByteBuf(
    val bytes: ByteArray,
) {
    inline fun <reified T> rows(): List<T> {
        return Json.mapper.readValue(bytes)
    }
}

enum class ItemType {
    Currency,
    Consumable,
    Equipment,
    Material,
}

data class AttributeBonus(
    val attr: String,
    val value: Int,
)

data class RewardConfig(
    val itemId: Int,
    val count: Int,
)

data class ItemConfig(
    val id: Int,
    val name: String,
    val type: ItemType,
    val quality: Int,
    val maxStack: Int,
    val sellPrice: Int,
    val attributes: List<AttributeBonus> = emptyList(),
)

data class MonsterConfig(
    val id: Int,
    val name: String,
    val level: Int,
    val hp: Long,
    val sceneId: Int,
    val skillIds: List<Int>,
    val rewards: List<RewardConfig>,
)

data class DropEntryConfig(
    val itemId: Int,
    val minCount: Int,
    val maxCount: Int,
    val weight: Int,
)

data class DropPoolConfig(
    val id: Int,
    val rolls: Int,
    val entries: List<DropEntryConfig>,
)

data class Vector2Int(
    val x: Int,
    val y: Int,
)

data class RectConfig(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class SceneConfig(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val spawnPoints: List<Vector2Int>,
    val safeZones: List<RectConfig>,
)

data class ActivityConfig(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val unlockLevel: Int,
    val conditions: Map<String, Int>,
    val rewards: List<RewardConfig>,
)

class TbItem(byteBuf: GameConfigByteBuf) : GameMapConfigTable<Int, ItemConfig>(
    name = "items",
    keyType = Int::class,
    rowType = ItemConfig::class,
    rows = byteBuf.rows<ItemConfig>().associateBy { it.id },
) {
    fun byType(type: ItemType): List<ItemConfig> {
        return all().filter { it.type == type }
    }
}

class TbMonster(byteBuf: GameConfigByteBuf) : GameMapConfigTable<Int, MonsterConfig>(
    name = "monsters",
    keyType = Int::class,
    rowType = MonsterConfig::class,
    rows = byteBuf.rows<MonsterConfig>().associateBy { it.id },
)

class TbDropPool(byteBuf: GameConfigByteBuf) : GameMapConfigTable<Int, DropPoolConfig>(
    name = "drop_pools",
    keyType = Int::class,
    rowType = DropPoolConfig::class,
    rows = byteBuf.rows<DropPoolConfig>().associateBy { it.id },
)

class TbScene(byteBuf: GameConfigByteBuf) : GameMapConfigTable<Int, SceneConfig>(
    name = "scenes",
    keyType = Int::class,
    rowType = SceneConfig::class,
    rows = byteBuf.rows<SceneConfig>().associateBy { it.id },
)

class TbActivity(byteBuf: GameConfigByteBuf) : GameMapConfigTable<String, ActivityConfig>(
    name = "activities",
    keyType = String::class,
    rowType = ActivityConfig::class,
    rows = byteBuf.rows<ActivityConfig>().associateBy { it.id },
)

abstract class GameMapConfigTable<K : Any, R : Any>(
    name: String,
    override val keyType: KClass<K>,
    override val rowType: KClass<R>,
    rows: Map<K, R>,
) : ConfigTable<K, R> {
    override val name: ConfigTableName = ConfigTableName(name)
    private val rows: Map<K, R> = rows.toMap()

    override val size: Int get() = rows.size

    override val ids: Set<K> get() = rows.keys

    override fun get(id: K): R? {
        return rows[id]
    }

    override fun all(): Collection<R> {
        return rows.values
    }
}
