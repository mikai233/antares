package com.mikai233.shared.excel

/**
 * Excel行数据
 * @param rowIndex 行号 从0开始
 * @param index 列名到列号的映射
 * @param data 列号到数据的映射
 */
data class Row(
    val rowIndex: Int,
    val index: Map<String, Int>,
    val data: Map<Int, String?>,
) {
    var currentName: String = ""

    private fun getValueByName(name: String): String {
        currentName = name
        val columnIndex = index[name] ?: throw IllegalArgumentException("Column `$name` not found")
        return data[columnIndex] ?: ""
    }

    fun parseString(name: String): String {
        currentName = name
        return getValueByName(name)
    }

    fun parseInt(name: String): Int {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            0
        } else {
            value.toInt()
        }
    }

    fun parseLong(name: String): Long {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            0L
        } else {
            value.toLong()
        }
    }

    fun parseBoolean(name: String): Boolean {
        currentName = name
        val value = getValueByName(name)
        return when {
            value.isBlankOrDefault() -> false
            value == "1" -> true
            value == "0" -> false
            else -> value.toBooleanStrict()
        }
    }

    fun parseFloat(name: String): Float {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            0f
        } else {
            value.toFloat()
        }
    }

    fun parseDouble(name: String): Double {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            0.0
        } else {
            value.toDouble()
        }
    }

    fun parseIntPair(name: String): Pair<Int, Int> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            Pair(0, 0)
        } else {
            val (first, second) = value.split(",")
                .also { list -> check(list.size == 2) { "expect 2 values: $list" } }
            Pair(first.toInt(), second.toInt())
        }
    }

    fun parseIntTriple(name: String): Triple<Int, Int, Int> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            Triple(0, 0, 0)
        } else {
            val (first, second, third) = value.split(",")
                .also { list -> check(list.size == 3) { "expect 3 values: $list" } }
            Triple(first.toInt(), second.toInt(), third.toInt())
        }
    }

    fun parseLongPair(name: String): Pair<Long, Long> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            Pair(0L, 0L)
        } else {
            val (first, second) = value.split(",")
                .also { list -> check(list.size == 2) { "expect 2 values: $list" } }
            Pair(first.toLong(), second.toLong())
        }
    }

    fun parseLongTriple(name: String): Triple<Long, Long, Long> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            Triple(0L, 0L, 0L)
        } else {
            val (first, second, third) = value.split(",")
                .also { list -> check(list.size == 3) { "expect 3 values: $list" } }
            Triple(first.toLong(), second.toLong(), third.toLong())
        }
    }

    fun parseIntArray(name: String): List<Int> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(",").map { it.toInt() }
        }
    }

    fun parseIntPairArray(name: String): List<Pair<Int, Int>> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(";").map {
                val (first, second) = it.split(",")
                    .also { list -> check(list.size == 2) { "expect 2 values: $list" } }
                Pair(first.toInt(), second.toInt())
            }
        }
    }

    fun parseIntTripleArray(name: String): List<Triple<Int, Int, Int>> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(";").map {
                val (first, second, third) = it.split(",")
                    .also { list -> check(list.size == 3) { "expect 3 values: $list" } }
                Triple(first.toInt(), second.toInt(), third.toInt())
            }
        }
    }

    fun parseLongArray(name: String): List<Long> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(",").map { it.toLong() }
        }
    }

    fun parseLongPairArray(name: String): List<Pair<Long, Long>> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(";").map {
                val (first, second) = it.split(",")
                    .also { list -> check(list.size == 2) { "expect 2 values: $list" } }
                Pair(first.toLong(), second.toLong())
            }
        }
    }

    fun parseLongTripleArray(name: String): List<Triple<Long, Long, Long>> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyList()
        } else {
            value.split(";").map {
                val (first, second, third) = it.split(",")
                    .also { list -> check(list.size == 3) { "expect 3 values: $list" } }
                Triple(first.toLong(), second.toLong(), third.toLong())
            }
        }
    }

    fun parseStringFloatMap(name: String): Map<String, Float> {
        currentName = name
        val value = getValueByName(name)
        return if (value.isBlankOrDefault()) {
            emptyMap()
        } else {
            value.split(";").associate {
                val (k, v) = it.split(",")
                    .also { list -> check(list.size == 2) { "expect 2 values: $list" } }
                k to v.toFloat()
            }
        }
    }

    private fun String.isBlankOrDefault(): Boolean {
        return this.isBlank() || this == "0"
    }
}