package com.mikai233.shared.excel

import com.alibaba.excel.context.AnalysisContext
import com.alibaba.excel.event.AnalysisEventListener
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.mikai233.common.extension.logger
import kotlin.reflect.KClass

typealias K = KClass<out GameConfigs<*, *>>

typealias V = GameConfigs<*, *>

interface GameConfig<K : Any> {
    fun id(): K
}

abstract class GameConfigs<K : Any, C : GameConfig<K>> : AnalysisEventListener<Map<Int, String?>>(), Map<K, C> {

    val logger = logger()

    lateinit var manager: GameConfigManager
        internal set

    //表头名称到列号的映射
    private val nameIndex: MutableMap<String, Int> = mutableMapOf()

    //列号到数据类型的映射
    private val typeIndex: MutableMap<Int, String> = mutableMapOf()

    private var configs: HashMap<K, C> = hashMapOf()

    override fun invoke(
        data: Map<Int, String?>,
        context: AnalysisContext
    ) {
        val rowIndex = context.readRowHolder().rowIndex
        val row = Row(rowIndex, nameIndex, data)
        try {
            val config = parseRow(row)
            check(configs.containsKey(config.id()).not()) { "Duplicate id:${config.id()} at row ${rowIndex + 1}" }
            configs[config.id()] = config
        } catch (e: Exception) {
            val index = nameIndex[row.currentName]
            val type = typeIndex[index]
            val validateError =
                ValidateError(
                    excelName(),
                    null,
                    rowIndex,
                    "解析`${row.currentName}`:`$type`失败:[${e::class.simpleName}]${e.message}"
                )
            manager.errors.getOrPut(excelName()) { arrayListOf() }.add(validateError)
        }
    }

    override fun invokeHeadMap(
        headMap: Map<Int, String?>,
        context: AnalysisContext
    ) {
        val rowIndex = context.readRowHolder().rowIndex
        //解析名字
        if (rowIndex == 0) {
            headMap.forEach { (k, v) ->
                nameIndex[requireNotNull(v) { "null value at row 1 column ${k + 1}" }] = k
            }
        } else if (rowIndex == 1) {
            headMap.forEach { (k, v) ->
                typeIndex[k] = requireNotNull(v) { "null value at row 2 column ${k + 1}" }
            }
        }
    }

    override fun doAfterAllAnalysed(context: AnalysisContext) = Unit

    abstract fun parseRow(row: Row): C

    abstract fun excelName(): String

    abstract fun ids(): Set<K>

    fun getById(id: K): C {
        return requireNotNull(configs[id]) { "id: $id not found in ${this::class.simpleName}" }
    }

    /**
     * 解析完[configs]后可以基于[configs]重新构建一些数据结构
     * 例如：将[configs]中的数据按照某个字段分组 或者 构建一些索引
     */
    abstract fun parseComplete()

    abstract fun validate()

    fun C.validate(block: ValidateScope<C, K>.(C) -> Unit) {
        val validateScope = ValidateScope(this)
        block(validateScope, this)
        if (validateScope.errors.isNotEmpty()) {
            manager.errors.getOrPut(excelName()) { arrayListOf() }.addAll(validateScope.errors)
        }
    }

    fun addError(message: String) {
        val validateError = ValidateError(excelName(), null, null, message)
        manager.errors.getOrPut(excelName()) { arrayListOf() }.add(validateError)
    }

    /**
     * 将[configs]进行序列化
     */
    fun serialize(kryo: Kryo, output: Output) {
        kryo.writeObject(output, configs)
    }

    /**
     * 将[configs]进行反序列化
     */
    fun deserialize(kryo: Kryo, input: Input) {
        @Suppress("UNCHECKED_CAST")
        configs = kryo.readObject(input, HashMap::class.java) as HashMap<K, C>
    }

    override val size: Int
        get() = configs.size
    override val entries: Set<Map.Entry<K, C>>
        get() = configs.entries
    override val keys: Set<K>
        get() = configs.keys
    override val values: Collection<C>
        get() = configs.values

    override fun containsKey(key: K): Boolean {
        return configs.containsKey(key)
    }

    override fun containsValue(value: C): Boolean {
        return configs.containsValue(value)
    }

    override fun get(key: K): C? {
        return configs[key]
    }

    override fun isEmpty(): Boolean {
        return configs.isEmpty()
    }
}