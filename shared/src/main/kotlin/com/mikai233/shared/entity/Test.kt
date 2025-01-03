package com.mikai233.shared.entity

import com.mikai233.common.db.Entity

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/3
 */
data class Example(
    val name: String,
    val age: Int,
    val items: ArrayList<Item>,
    val itemMap: Map<String, Item>,
    val itemSet: Set<Item2>
) : Entity

data class Item(
    val id: Int,
    val description: String
)

data class Item2(
    val id: Int,
    val scores: LinkedHashSet<Double>
)