package com.mikai233.common.conf

import com.mikai233.common.excel.ExcelConfig
import com.mikai233.common.excel.ExcelManager
import com.mikai233.common.ext.logger

object GlobalExcel {
    private val logger = logger()

    @Volatile
    lateinit var manager: ExcelManager
        private set

    fun initialized() = this::manager.isInitialized

    @Synchronized
    fun updateManager(manager: ExcelManager) {
        if (initialized()) {
            val commitId = this.manager.commitId
            val version = this.manager.version
            val generateTime = this.manager.generateTime
            this.manager = manager
            logger.info(
                "excel manager updated, commitId:{} version:{} generateTime:{} => commitId:{} version:{} generateTime:{}",
                commitId,
                version,
                generateTime,
                manager.commitId,
                manager.version,
                manager.generateTime
            )
        } else {
            this.manager = manager
            logger.info(
                "excel manager init: commitId:{} version:{} generateTime:{}",
                manager.commitId,
                manager.version,
                manager.generateTime
            )
        }
    }

    inline fun <reified T : ExcelConfig<*, *>> getConfig() = manager.getConfig<T>()
}