package com.mikai233.shared.excel

import com.alibaba.excel.EasyExcel
import com.mikai233.common.extension.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class GameConfigManager(private val version: String) {
    //这个是给Kryo通过反射构造用的
    @Suppress("unused")
    constructor() : this("Unknown")

    companion object {
        const val HEADER_SIZE = 5
    }

    @Transient
    var logger = logger()
        private set

    @Transient
    var errors: MutableMap<String, MutableList<ValidateError>> = mutableMapOf()
        private set

    @Transient
    var configs: HashMap<K, V> = hashMapOf()
        internal set

    inline fun <reified T : V> get(): T {
        return requireNotNull(configs[T::class]) { "GameConfigs not found: ${T::class.simpleName}" } as T
    }

    inline fun <reified T : GameConfigs<K, C>, C : GameConfig<K>, K : Any> getById(id: K): C {
        return get<T>().getById(id)
    }

    /**
     * @param excelDir excel文件夹路径
     * @throws IllegalStateException 如果已经加载过配置表
     * @throws IllegalArgumentException 如果配置表类没有空构造函数
     */
    suspend fun load(excelDir: String) {
        check(configs.isEmpty()) { "GameConfigManager already loaded" }
        val loadedGameConfigs = coroutineScope {
            ConfigsImpl.map { configClazz ->
                val primaryConstructor =
                    requireNotNull(configClazz.primaryConstructor) { "GameConfigs ${configClazz.simpleName} must have a empty primary constructor" }
                async(Dispatchers.IO) {
                    val gameConfigs = primaryConstructor.call()
                    gameConfigs.manager = this@GameConfigManager
                    val path = "$excelDir/${gameConfigs.excelName()}".replace("\\", "/")
                    logger.info("Loading ${gameConfigs.excelName()} from $path")
                    EasyExcel.read(path, gameConfigs).headRowNumber(HEADER_SIZE).sheet().doRead()
                    gameConfigs
                }
            }.awaitAll()
        }
        loadedGameConfigs.forEach { gameConfigs ->
            configs[gameConfigs::class] = gameConfigs
        }
        loadComplete()
    }

    /**
     * 所有配置表加载完成之后重建配置表数据结构以及校验
     */
    fun loadComplete() {
        val completeFirstClasses = completeFirst()
        val completeSecondClasses = configs.keys.filter { it !in completeFirstClasses }
        (completeFirstClasses + completeSecondClasses).forEach { configClazz ->
            val gameConfigs =
                requireNotNull(configs[configClazz]) { "GameConfigs not found: ${configClazz.simpleName}" }
            gameConfigs.parseComplete()
        }
        configs.values.forEach {
            ValidateThreadLocal.set(it.excelName(), this)
            it.validate()
            ValidateThreadLocal.remove()
        }
        val flattenErrors = errors.values.flatten()
        errors.clear()
        if (flattenErrors.isNotEmpty()) {
            flattenErrors.forEach { validateError ->
                logger.error(validateError.toString())
            }
            throw IllegalStateException("GameConfigManager validate failed")
        }
    }

    /**
     * 优先构造完成的配置表
     */
    private fun completeFirst(): List<KClass<out V>> {
        return listOf()
    }

    override fun toString(): String {
        return "GameConfigManager(version='$version', configs=$configs)"
    }
}