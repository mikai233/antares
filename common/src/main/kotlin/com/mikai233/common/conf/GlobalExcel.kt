package com.mikai233.common.conf

import com.mikai233.common.excel.ConfigMapValue
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
            val generateTime = this.manager.generateTime
            this.manager = manager
            logger.info(
                "excel manager[{}] updated, commitId:{} generateTime:{} => commitId:{} generateTime:{}",
                GlobalData.version,
                commitId,
                generateTime,
                manager.commitId,
                manager.generateTime
            )
        } else {
            this.manager = manager
            logger.info(
                "excel manager[{}] init: commitId:{} generateTime:{}",
                GlobalData.version,
                manager.commitId,
                manager.generateTime
            )
        }
    }

    inline fun <reified T : ConfigMapValue> getConfig() = manager.getConfig<T>()
}
