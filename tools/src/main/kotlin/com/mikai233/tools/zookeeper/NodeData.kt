package com.mikai233.tools.zookeeper

/**
 * @author mikai233
 * @email dreamfever2017@yahoo.com
 * @date 2025/1/9
 */
data class NodeData(
    val name: String,
    val data: Any?,
    val children: List<NodeData>?,
)
