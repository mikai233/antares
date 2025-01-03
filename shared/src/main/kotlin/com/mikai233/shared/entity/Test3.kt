package com.mikai233.shared.entity

import com.mikai233.common.db.Entity

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/3
 */
data class Example3(
    val name: String,
    val age: Int,
    val items: ArrayList<Item>,
    val itemMap: Map<String, Item>,
    val itemSet: Set<Item3>,
    val test3: Test3,
) : Entity

data class Item3(val name: String, val age: Int, val memorized: Boolean)

enum class Test3 {
    A, B, C, D, E, F, G
}