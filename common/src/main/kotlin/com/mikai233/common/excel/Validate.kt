package com.mikai233.common.excel

object ValidateThreadLocal {
    private val threadLocal = ThreadLocal<Pair<String, GameConfigManager>>()

    fun set(excelName: String, manager: GameConfigManager) {
        threadLocal.set(excelName to manager)
    }

    fun get(): Pair<String, GameConfigManager> {
        return requireNotNull(threadLocal.get()) { "ValidateThreadLocal not set" }
    }

    fun remove() {
        threadLocal.remove()
    }
}

/**
 * @param rowIndex 从0开始
 */
data class ValidateError(
    val excelName: String,
    val id: String?,
    val rowIndex: Int?,
    val message: String,
) {
    override fun toString(): String {
        return "[$excelName]${if (id != null) "id:[$id]" else ""}" +
            "${if (rowIndex != null) "行:[${rowIndex + 1}]" else ""} " +
            "error: $message"
    }
}

class ValidateScope<C, K>(val config: C) where C : GameConfig<K>, K : Any {
    val errors = mutableListOf<ValidateError>()
    val manager: GameConfigManager
    private val excelName: String

    init {
        val (e, m) = ValidateThreadLocal.get()
        manager = m
        excelName = e
    }

    /**
     * 校验A配置表中某个字段是否为B配置表的主键
     */
    inline fun <reified C : GameConfigs<K, *>, K : Any> refFor(id: K) {
        val referenceConfig = manager.get<C>()
        if (!referenceConfig.contains(id)) {
            addError("在${referenceConfig.excelName()}中未找到引用")
        }
    }

    /**
     * 校验配置表中的字段是否是不重复的且升序排列
     */
    fun <T> isUniqueSorted(fieldName: String, values: Iterable<T>) where T : Comparable<T> {
        values.zipWithNext { a, b ->
            if (a >= b) {
                addError("${fieldName}不是升序排列")
            }
        }
    }

    /**
     * 校验配置表中的字段全部大于0
     */
    fun isPositive(fieldName: String, values: Iterable<Int>) {
        values.forEach {
            if (it <= 0) {
                addError("${fieldName}小于等于0")
            }
        }
    }

    /**
     * 添加一条诊断信息
     */
    fun addError(message: String) {
        errors.add(ValidateError(excelName, config.id().toString(), null, message))
    }
}
