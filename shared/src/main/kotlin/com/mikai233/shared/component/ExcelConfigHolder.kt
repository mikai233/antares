package com.mikai233.shared.component

import com.google.common.collect.ImmutableMap
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.conf.GlobalExcel
import com.mikai233.common.core.component.ZookeeperConfigCenter
import com.mikai233.common.core.component.config.excelFile
import com.mikai233.common.core.component.config.excelVersion
import com.mikai233.common.core.component.config.getConfigEx
import com.mikai233.common.excel.*
import com.mikai233.common.ext.logger
import com.mikai233.common.inject.XKoin
import com.mikai233.shared.serde.ProtobufExcelSerde
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExcelConfigHolder(
    private val koin: XKoin,
    private val serde: ExcelSerde = ProtobufExcelSerde
) :
    KoinComponent by koin {
    private val logger = logger()
    private val configCenter: ZookeeperConfigCenter by inject()

    companion object {
        const val name = "configs.bin"
    }

    init {
        initExcelConfig()
    }

    private fun initExcelConfig() {
        if (!GlobalExcel.initialized()) {
            //sync init
            val version = GlobalData.version
            val manager = configCenter.getConfigEx<ExcelManager>(excelVersion(version))
            val configBytes = configCenter.getConfig(excelFile(version, name))
            val configs = loadConfigs(configBytes)
            manager.loadFromConfigs(configs)
            GlobalExcel.updateManager(manager)
            configCenter.watchConfig(excelFile(version, name)) {
                try {
                    val updateMgr = configCenter.getConfigEx<ExcelManager>(excelVersion(version))
                    val updateCfg = loadConfigs(it)
                    updateMgr.loadFromConfigs(updateCfg)
                    GlobalExcel.updateManager(updateMgr)
                } catch (e: Exception) {
                    logger.error("update excel error", e)
                }
            }
        } else {
            logger.info("excel manager already init:{}", GlobalExcel.manager)
        }
    }

    private fun loadConfigs(bytes: ByteArray): ImmutableConfigMap {
        val configs = ImmutableMap.builder<ConfigMapKey, ConfigMapValue>()
        val serdeConfigs = serde.de(bytes)
        serdeConfigs.configs.forEach { config ->
            @Suppress("UNCHECKED_CAST")
            configs.put(config::class as ConfigMapKey, config as ConfigMapValue)
        }
        return configs.build()
    }
}
