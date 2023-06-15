package com.mikai233.shared.assets

fun assetPackage(block: AssetPackageBuilder.() -> Unit): AssetPackage {
    val builder = AssetPackageBuilder()
    block.invoke(builder)
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
