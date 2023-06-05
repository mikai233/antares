package com.mikai233.tools.excel

import com.mikai233.common.conf.GlobalData
import com.mikai233.common.conf.GlobalEnv
import com.mikai233.common.core.component.config.excelFile
import com.mikai233.common.core.component.config.excelVersion
import com.mikai233.common.excel.ExcelManager
import com.mikai233.common.excel.SerdeConfigs
import com.mikai233.common.ext.Json
import com.mikai233.common.ext.buildSimpleZkClient
import com.mikai233.common.ext.logger
import com.mikai233.shared.serde.ProtobufExcelSerde
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

object ExcelExporter {
    private val logger = logger()

    @JvmStatic
    fun main(args: Array<String>) {
        check(args.size == 1) { "please input excel dir" }
        val excelDir = args[0]
        val now = Clock.System.now().toLocalDateTime(GlobalData.zoneId)
        val version = GlobalData.version
        val client = buildSimpleZkClient(GlobalEnv.zkConnect).apply { start() }
        val manager = ExcelManager("n/a", now.toString())
        manager.loadFromExcel(excelDir, "com.mikai233.shared.excel")
        client.create().orSetData().creatingParentsIfNeeded()
            .forPath(excelVersion(version), Json.toJsonBytes(manager, true))
        val serdeConfigs = SerdeConfigs(manager.configs.values.toSet())
        val serBytes = ProtobufExcelSerde.ser(serdeConfigs)
        client.create().orSetData().forPath(excelFile(version, "configs.bin"), serBytes)
        logger.info("export excel to zookeeper:{}", manager)
    }
}
