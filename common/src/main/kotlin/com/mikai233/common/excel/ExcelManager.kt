package com.mikai233.common.excel

import com.google.common.collect.ImmutableMap
import com.mikai233.common.ext.logger
import org.reflections.Reflections
import kotlin.reflect.KClass

class ExcelManager {
    private val logger = logger()
    val validators: HashMap<KClass<out Validator<ExcelRow<*>, *>>, Validator<in ExcelRow<*>, *>> = hashMapOf()
    var commitId: String = ""
        private set
    var version: String = ""
        private set
    var generateTime: String = ""
        private set
    lateinit var configs: ImmutableMap<KClass<out ExcelConfig<*, *>>, ExcelConfig<*, *>>
        private set

    fun loadExcel(excelDir: String, vararg packages: String) {
        val configsBuilder = ImmutableMap.builder<KClass<out ExcelConfig<*, *>>, ExcelConfig<*, *>>()
        Reflections(packages).getSubTypesOf(ExcelConfig::class.java).forEach { clazz ->
            val config = clazz.getConstructor(ExcelManager::class.java).newInstance(this)
            configsBuilder.put(clazz.kotlin, config)
        }
        configs = configsBuilder.build()
        configs.values.forEach { config ->
            logger.info("load:{}", config.name())
            with(config) {
                load(EasyExcelContext("$excelDir/${name()}"))
                rebuildData()
            }
        }
        configs.values.forEach(ExcelConfig<*, *>::allLoadFinish)
    }

    fun validate() {

    }

    inline fun <reified T : ExcelConfig<*, *>> getConfig(): T {
        return requireNotNull(configs[T::class]) { "config:${T::class} not found" } as T
    }
}