package com.mikai233.common.excel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableMap
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.core.component.config.Config
import com.mikai233.common.core.component.config.excelVersion
import com.mikai233.common.ext.logger
import org.reflections.Reflections
import kotlin.reflect.KClass

val validators: HashMap<KClass<out Validator<ExcelRow<*>, *>>, Validator<in ExcelRow<*>, *>> = hashMapOf()
typealias ConfigMapKey = KClass<out ExcelConfig<*, *>>
typealias ConfigMapValue = ExcelConfig<*, *>
typealias ImmutableConfigMap = ImmutableMap<ConfigMapKey, ConfigMapValue>

class ExcelManager(val commitId: String, val generateTime: String) : Config {
    @JsonIgnore
    private val logger = logger()

    @JsonIgnore
    lateinit var configs: ImmutableConfigMap
        private set

    private fun isInitialized() = this::configs.isInitialized

    fun loadFromExcel(excelDir: String, vararg packages: String) {
        check(!isInitialized()) { "already loaded" }
        val configsBuilder = ImmutableMap.builder<ConfigMapKey, ConfigMapValue>()
        Reflections(packages).getSubTypesOf(ExcelConfig::class.java).forEach { clazz ->
            val config = clazz.getConstructor().newInstance()
            configsBuilder.put(clazz.kotlin, config)
        }
        configs = configsBuilder.build()
        configs.values.forEach { config ->
            logger.info("load:{}", config.name())
            with(config) {
                load(EasyExcelContext("$excelDir/${name()}"), this@ExcelManager)
                rebuildData(this@ExcelManager)
            }
        }
        configs.values.forEach {
            it.allLoadFinish(this)
        }
    }

    fun loadFromConfigs(configs: ImmutableMap<ConfigMapKey, ConfigMapValue>) {
        check(!isInitialized()) { "already loaded" }
        this.configs = configs
        this.configs.values.forEach { config ->
            config.rebuildData(this)
        }
        this.configs.values.forEach {
            it.allLoadFinish(this)
        }
    }

    fun validate() {

    }

    inline fun <reified T : ConfigMapValue> getConfig(): T {
        return requireNotNull(configs[T::class]) { "config:${T::class} not found" } as T
    }


    override fun path(): String {
        return excelVersion(GlobalData.version)
    }

    override fun toString(): String {
        return "ExcelManager(commitId='$commitId', generateTime='$generateTime')"
    }
}