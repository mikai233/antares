package com.mikai233.common.excel

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableMap
import com.mikai233.common.conf.GlobalData
import com.mikai233.common.core.component.config.Config
import com.mikai233.common.core.component.config.excelVersion
import com.mikai233.common.ext.logger
import org.reflections.Reflections
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

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
    private val reportMessage: ArrayList<String> = arrayListOf()


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
        validateConfig()
        configs.values.forEach {
            it.allLoadFinish(this)
        }
        checkReport()
    }

    fun loadFromConfigs(configs: ImmutableMap<ConfigMapKey, ConfigMapValue>) {
        check(!isInitialized()) { "already loaded" }
        this.configs = configs
        this.configs.values.forEach { config ->
            config.rebuildData(this)
        }
        validateConfig()
        this.configs.values.forEach {
            it.allLoadFinish(this)
        }
        checkReport()
    }

    private fun validateConfig() {
        configs.values.forEach { config ->
            config.forEach { (key, row) ->
                row::class.memberProperties.forEach { columnProperty ->
                    val refFor = columnProperty.findAnnotation<RefFor>()
                    if (refFor != null) {
                        val refForConfig = configs[refFor.config]
                        if (refForConfig == null) {
                            config.report(this, "refFor config:${refFor.config} not found")
                        } else {
                            val refValue = columnProperty.call(row)
                            if (refValue is Iterable<*>) {
                                refValue.forEach {
                                    if (!refForConfig.rows.containsKey(it)) {
                                        config.report(
                                            this,
                                            "refFor id:${refValue} not found in config:${refFor.config}"
                                        )
                                    }
                                }
                            } else {
                                if (!refForConfig.rows.containsKey(refValue)) {
                                    config.report(this, "refFor id:${refValue} not found in config:${refFor.config}")
                                }
                            }
                        }
                    }
                }
            }
        }
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

    fun report(message: String) {
        reportMessage.add(message)
    }

    private fun checkReport() {
        if (reportMessage.isNotEmpty()) {
            reportMessage.forEach {
                logger.error("{}", it)
            }
            error("config validate error")
        }
    }
}
