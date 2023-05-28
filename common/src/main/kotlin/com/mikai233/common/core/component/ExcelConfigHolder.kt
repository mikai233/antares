package com.mikai233.common.core.component

import com.google.common.collect.ImmutableMap
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.conf.GlobalExcel
import com.mikai233.common.core.component.config.excelFile
import com.mikai233.common.core.component.config.excelVersion
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.excel.ConfigMapKey
import com.mikai233.common.excel.ConfigMapValue
import com.mikai233.common.excel.ExcelManager
import com.mikai233.common.excel.ImmutableConfigMap
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.logger
import com.mikai233.common.inject.XKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExcelConfigHolder(private val koin: XKoin) : KoinComponent by koin {
    private val logger = logger()
    private val configCenter: ZookeeperConfigCenter by inject()

    init {
        initExcelConfig()
    }

    private fun initExcelConfig() {
        if (!GlobalExcel.initialized()) {
            val version = GlobalData.version
            val manager = configCenter.getConfigEx<ExcelManager>(excelVersion(version))
            val configs = loadConfigs(version)
            manager.loadFromConfigs(configs)
            GlobalExcel.updateManager(manager)
            configCenter.watchConfig(excelVersion(version)) {
                try {
                    val newManager = Json.fromJson<ExcelManager>(it)
                    val newConfigs = loadConfigs(GlobalData.version)
                    newManager.loadFromConfigs(newConfigs)
                    GlobalExcel.updateManager(newManager)
                } catch (e: Exception) {
                    logger.error("update excel error", e)
                }
            }
        } else {
            logger.info("excel manager already init:{}", GlobalExcel.manager)
        }
    }

    private fun loadConfigs(version: String): ImmutableConfigMap {
        val configs = ImmutableMap.builder<ConfigMapKey, ConfigMapValue>()
        configCenter.getChildren(excelVersion(version)).forEach { fileName ->
            val bytes = configCenter.getConfig(excelFile(version, fileName))
            val config = Json.fromJson<ConfigMapValue>(bytes)
            configs.put(config::class, config)
        }
        return configs.build()
    }
}