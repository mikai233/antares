package com.mikai233.common.excel

import com.mikai233.common.ext.logger
import org.reflections.Reflections
import kotlin.reflect.KClass

class ExcelManager {
    private val logger = logger()
    val validators: HashMap<KClass<out ExcelConfig<*>>, Validator<in ExcelRow<*>, *>> = hashMapOf()
    private val configs: HashMap<KClass<out ExcelConfig<*>>, ExcelConfig<*>> = hashMapOf()

    fun loadExcel(vararg packages: String) {
        Reflections(packages).getSubTypesOf(ExcelConfig::class.java).forEach { clazz ->
            val config = clazz.getConstructor(ExcelManager::class.java).newInstance(this)
            configs[clazz.kotlin] = config
        }
        configs.values.forEach { config ->
            logger.info("load:{}", config.name())
            with(config) {
                load()
                rebuildData()
            }
        }
        configs.values.forEach(ExcelConfig<*>::allLoadFinish)
    }

    fun validate() {

    }

    fun getConfigs() = configs.toMap()

    inline fun <reified T : ExcelConfig<*>> getConfig(): ExcelConfig<*> {
        return requireNotNull(getConfigs()[T::class]) { "config:${T::class} not found" }
    }
}