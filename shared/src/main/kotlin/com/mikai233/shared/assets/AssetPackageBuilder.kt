package com.mikai233.shared.assets

class AssetPackageBuilder private constructor(
    private val assets: MutableList<Asset> = mutableListOf(),
    private var mergeAsset: Boolean = true
) {

    fun autoMerge(merge: Boolean): AssetPackageBuilder {
        mergeAsset = merge
        return this
    }

    fun mergePrevious(): AssetPackageBuilder {
        TODO()
    }

    operator fun plus(asset: Asset): AssetPackageBuilder {
        assets.add(asset)
        return this
    }

    fun build(): AssetPackage = AssetPackage(assets, mergeAsset)
}
