package com.mikai233.common.test

import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.config.luban.ActivityRow
import com.mikai233.common.config.luban.DroppoolRow
import com.mikai233.common.config.luban.ItemRow
import com.mikai233.common.config.luban.MonsterRow
import com.mikai233.common.config.luban.SceneRow
import io.github.realmlabs.asteria.config.ConfigTableName
import io.github.realmlabs.asteria.config.luban.LubanBinaryConfigLoader
import io.github.realmlabs.asteria.config.luban.MemoryLubanDataSource
import io.github.realmlabs.asteria.config.requireComponent
import io.github.realmlabs.asteria.config.requireTable
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
            ),
        ).load()
        val tables = snapshot.requireComponent<GameTables>()

        val itemTable = snapshot.requireTable<Int, ItemRow>(ConfigTableName("items"))
        val monsterTable = snapshot.requireTable<Int, MonsterRow>(ConfigTableName("monsters"))
        val dropPoolTable = snapshot.requireTable<Int, DroppoolRow>(ConfigTableName("droppools"))
        val sceneTable = snapshot.requireTable<Int, SceneRow>(ConfigTableName("scenes"))
        val activityTable = snapshot.requireTable<String, ActivityRow>(ConfigTableName("activities"))
        val sword = tables.getTbItem().require(3001)
        val wolf = tables.getTbMonster().require(101)
        val novicePlains = tables.getTbScene().require(1)
        val activity = activityTable.require("wolf_hunt")

        assertEquals(5, snapshot.tables().size)
        assertEquals("Iron Sword", itemTable.require(3001).name)
        assertEquals(com.mikai233.common.config.luban.gen.item.ItemType.Equipment, sword.type)
        assertEquals(1, sword.maxStack)
        assertEquals("Forest Wolf", monsterTable.require(101).name)
        assertEquals(listOf(1001, 1002), wolf.skillIds.toList())
        assertEquals(2, wolf.rewards.size)
        assertEquals(2, dropPoolTable.require(1).rolls)
        assertEquals(3, dropPoolTable.require(1).entries.size)
        assertEquals("Novice Plains", sceneTable.require(1).name)
        assertEquals(3, novicePlains.spawnPoints.size)
        assertEquals(2, novicePlains.safeZones.size)
        assertEquals("killCount=10,monsterId=101", activity.conditionSummary)
        assertEquals("1001x500,3001x1", activity.rewardSummary)
        assertEquals(1, tables.getTbItem().byType(com.mikai233.common.config.luban.gen.item.ItemType.Equipment).size)
        assertNotNull(tables.getTbActivity().get("daily_login"))
    }
}
