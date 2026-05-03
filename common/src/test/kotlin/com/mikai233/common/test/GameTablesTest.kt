package com.mikai233.common.test

import com.mikai233.common.config.luban.ActivityConfig
import com.mikai233.common.config.luban.DemoGameConfigArtifacts
import com.mikai233.common.config.luban.GameConfigSnapshotLoader
import com.mikai233.common.config.luban.GameTables
import com.mikai233.common.config.luban.ItemConfig
import com.mikai233.common.config.luban.ItemType
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
    fun demoLubanTablesCoverCommonConfigShapes() = runBlocking {
        val snapshot = GameConfigSnapshotLoader(
            LubanBinaryConfigLoader(
                tablesType = GameTables::class,
                dataSource = MemoryLubanDataSource(DemoGameConfigArtifacts.bytesByPath()),
            ),
        ).load()
        val tables = snapshot.requireComponent<GameTables>()

        val itemTable = snapshot.requireTable<Int, ItemConfig>(ConfigTableName("items"))
        val activityTable = snapshot.requireTable<String, ActivityConfig>(ConfigTableName("activities"))
        val sword = tables.getTbItem().require(3001)
        val wolf = tables.getTbMonster().require(101)
        val dropPool = tables.getTbDropPool().require(1)
        val scene = tables.getTbScene().require(1)
        val activity = activityTable.require("wolf_hunt")

        assertEquals(5, snapshot.tables().size)
        assertEquals("Iron Sword", itemTable.require(3001).name)
        assertEquals(listOf("atk", "crit"), sword.attributes.map { it.attr })
        assertEquals(listOf(10001, 10002), wolf.skillIds)
        assertEquals(3, dropPool.entries.size)
        assertEquals(120, scene.spawnPoints.first().x)
        assertEquals(101, activity.conditions["monsterId"])
        assertEquals(1, tables.getTbItem().byType(ItemType.Equipment).size)
        assertNotNull(tables.getTbActivity().get("daily_login"))
    }
}
