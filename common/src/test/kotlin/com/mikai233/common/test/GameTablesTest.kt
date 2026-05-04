package com.mikai233.common.test

import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.config.luban.GameConfigQueries
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.config.luban.GameTablesSnapshotBridge
import com.mikai233.common.config.luban.tbActivity
import com.mikai233.common.config.luban.tbDroppool
import com.mikai233.common.config.luban.tbGameGlobal
import com.mikai233.common.config.luban.tbItem
import com.mikai233.common.config.luban.tbMonster
import com.mikai233.common.config.luban.tbRotationMessage
import com.mikai233.common.config.luban.tbScene
import io.github.realmlabs.asteria.config.component
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GameTablesTest {
    @Test
    fun generatedLubanTablesLoadFromPublishedArtifacts() = runBlocking {
        val snapshot = GameConfigSnapshotLoader(
            LubanBinaryConfigLoader(
                tablesType = GameTables::class,
                dataSource = MemoryLubanDataSource(
                    LubanTestConfigArtifacts.unpackBundle(LubanTestConfigArtifacts.bundleBytes()),
                ),
                bridge = GameTablesSnapshotBridge,
            ),
        ).load()
        val queries = snapshot.component<GameConfigQueries>()

        val sword = snapshot.tbItem.require(3001)
        val wolf = snapshot.tbMonster.require(101)
        val novicePlains = snapshot.tbScene.require(1)
        val activity = snapshot.tbActivity.require("wolf_hunt")
        val firstRotationMessage = snapshot.tbRotationMessage.first()
        val gameGlobal = snapshot.tbGameGlobal.get()

        assertEquals(7, snapshot.tables().size)
        assertEquals("Iron Sword", snapshot.tbItem.require(3001).name)
        assertEquals(com.mikai233.common.config.luban.gen.item.ItemType.Equipment, sword.type)
        assertEquals(1, sword.maxStack)
        assertEquals("Forest Wolf", snapshot.tbMonster.require(101).name)
        assertEquals(listOf(1001, 1002), wolf.skillIds.toList())
        assertEquals(2, wolf.rewards.size)
        assertEquals(2, snapshot.tbDroppool.require(1).rolls)
        assertEquals(3, snapshot.tbDroppool.require(1).entries.size)
        assertEquals("Novice Plains", snapshot.tbScene.require(1).name)
        assertEquals(3, novicePlains.spawnPoints.size)
        assertEquals(2, novicePlains.safeZones.size)
        assertEquals("killCount=10,monsterId=101", activity.conditionSummary)
        assertEquals("1001x500,3001x1", activity.rewardSummary)
        assertEquals(3, snapshot.tbRotationMessage.size)
        assertEquals("Welcome to Antares", firstRotationMessage.content)
        assertEquals(1, firstRotationMessage.minLevel)
        assertEquals(1, gameGlobal.defaultWorldId)
        assertEquals(60, gameGlobal.maxPlayerLevel)
        assertEquals("No maintenance scheduled", gameGlobal.maintenanceNotice)
        assertEquals(
            1,
            snapshot.tbItem.all().count { row ->
                row.type == com.mikai233.common.config.luban.gen.item.ItemType.Equipment
            },
        )
        assertEquals(1, queries.itemsByType[com.mikai233.common.config.luban.gen.item.ItemType.Equipment]?.size)
        assertEquals(1, queries.monstersBySceneId[1]?.size)
        assertNotNull(snapshot.tbActivity.get("daily_login"))
    }
}
