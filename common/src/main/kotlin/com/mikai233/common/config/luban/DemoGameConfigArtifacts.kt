package com.mikai233.common.config.luban

import com.mikai233.common.extension.Json
import io.github.mikai233.asteria.config.publisher.ConfigPublicationArtifact

object DemoGameConfigArtifacts {
    fun artifacts(): List<ConfigPublicationArtifact> {
        return listOf(
            artifact("item_tbitem.bytes", items()),
            artifact("monster_tbmonster.bytes", monsters()),
            artifact("drop_tbdroppool.bytes", dropPools()),
            artifact("scene_tbscene.bytes", scenes()),
            artifact("activity_tbactivity.bytes", activities()),
        )
    }

    fun bytesByPath(): Map<String, ByteArray> {
        return artifacts().associate { it.relativePath to it.bytes }
    }

    private fun artifact(path: String, rows: Any): ConfigPublicationArtifact {
        return ConfigPublicationArtifact(path, Json.toBytes(rows, pretty = true))
    }

    private fun items(): List<ItemConfig> {
        return listOf(
            ItemConfig(1001, "Gold", ItemType.Currency, quality = 1, maxStack = 999999, sellPrice = 0),
            ItemConfig(2001, "Small Potion", ItemType.Consumable, quality = 2, maxStack = 99, sellPrice = 10),
            ItemConfig(
                id = 3001,
                name = "Iron Sword",
                type = ItemType.Equipment,
                quality = 3,
                maxStack = 1,
                sellPrice = 120,
                attributes = listOf(AttributeBonus("atk", 18), AttributeBonus("crit", 2)),
            ),
            ItemConfig(4001, "Wolf Fang", ItemType.Material, quality = 2, maxStack = 999, sellPrice = 3),
        )
    }

    private fun monsters(): List<MonsterConfig> {
        return listOf(
            MonsterConfig(
                id = 101,
                name = "Forest Wolf",
                level = 5,
                hp = 180,
                sceneId = 1,
                skillIds = listOf(10001, 10002),
                rewards = listOf(RewardConfig(1001, 25), RewardConfig(4001, 1)),
            ),
            MonsterConfig(
                id = 102,
                name = "Cave Guard",
                level = 12,
                hp = 950,
                sceneId = 2,
                skillIds = listOf(11001, 11002, 11003),
                rewards = listOf(RewardConfig(1001, 90), RewardConfig(2001, 2)),
            ),
        )
    }

    private fun dropPools(): List<DropPoolConfig> {
        return listOf(
            DropPoolConfig(
                id = 1,
                rolls = 1,
                entries = listOf(
                    DropEntryConfig(itemId = 1001, minCount = 10, maxCount = 30, weight = 7000),
                    DropEntryConfig(itemId = 2001, minCount = 1, maxCount = 2, weight = 2500),
                    DropEntryConfig(itemId = 3001, minCount = 1, maxCount = 1, weight = 500),
                ),
            ),
            DropPoolConfig(
                id = 2,
                rolls = 2,
                entries = listOf(
                    DropEntryConfig(itemId = 1001, minCount = 50, maxCount = 150, weight = 6000),
                    DropEntryConfig(itemId = 4001, minCount = 2, maxCount = 8, weight = 4000),
                ),
            ),
        )
    }

    private fun scenes(): List<SceneConfig> {
        return listOf(
            SceneConfig(
                id = 1,
                name = "Green Field",
                width = 1200,
                height = 800,
                spawnPoints = listOf(Vector2Int(120, 140), Vector2Int(320, 460)),
                safeZones = listOf(RectConfig(x = 40, y = 40, width = 180, height = 160)),
            ),
            SceneConfig(
                id = 2,
                name = "Crystal Cave",
                width = 960,
                height = 720,
                spawnPoints = listOf(Vector2Int(80, 120), Vector2Int(740, 600)),
                safeZones = listOf(RectConfig(x = 700, y = 560, width = 180, height = 120)),
            ),
        )
    }

    private fun activities(): List<ActivityConfig> {
        return listOf(
            ActivityConfig(
                id = "daily_login",
                name = "Daily Login",
                startTime = "2026-01-01T00:00:00",
                endTime = "2026-12-31T23:59:59",
                unlockLevel = 1,
                conditions = mapOf("loginDays" to 1),
                rewards = listOf(RewardConfig(1001, 100), RewardConfig(2001, 1)),
            ),
            ActivityConfig(
                id = "wolf_hunt",
                name = "Wolf Hunt",
                startTime = "2026-05-01T00:00:00",
                endTime = "2026-05-14T23:59:59",
                unlockLevel = 5,
                conditions = mapOf("monsterId" to 101, "killCount" to 10),
                rewards = listOf(RewardConfig(1001, 500), RewardConfig(3001, 1)),
            ),
        )
    }
}
