package com.mikai233.common.assets

/**
 * Entry point for asset package DSL construction.
 *
 * Example:
 * ```kotlin
 * val rewards = assetPackage {
 *     item(1001, 3)
 *     resource(2001, 500)
 * }
 * ```
 */
fun assetPackage(block: AssetPackageBuilder.() -> Unit): AssetPackage {
    val builder = AssetPackageBuilder()
    block(builder)
    return builder.build()
}

class ItemAssetBuilder {
    var id: Int = 0
    var num: Long = 0L

    fun build(): ItemAsset {
        return ItemAsset(id, num)
    }
}

class ResourceAssetBuilder {
    var id: Int = 0
    var num: Long = 0L

    fun build(): ResourceAsset {
        return ResourceAsset(id, num)
    }
}
