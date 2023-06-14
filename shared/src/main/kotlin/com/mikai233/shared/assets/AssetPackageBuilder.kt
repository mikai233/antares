package com.mikai233.shared.assets

class AssetPackageBuilder {
    private var assets: MutableList<Asset> = mutableListOf()
    private var mergeOnBuild: Boolean = true

    fun mergeOnBuild(merge: Boolean): AssetPackageBuilder {
        mergeOnBuild = merge
        return this
    }

    fun merge(): AssetPackageBuilder {
        assets = assets.groupBy { it.type }.mapNotNull { (_, typeAssets) ->
            if (typeAssets.isNotEmpty()) {
                typeAssets.reduce { acc, asset -> acc + asset }
            } else {
                null
            }
        }.toMutableList()
        return this
    }

    operator fun plus(asset: Asset): AssetPackageBuilder {
        assets.add(asset)
        return this
    }

    operator fun plus(otherAssets: List<Asset>): AssetPackageBuilder {
        assets.addAll(otherAssets)
        return this
    }

    operator fun plus(otherPackage: AssetPackage): AssetPackageBuilder {
        assets.addAll(otherPackage.assets)
        return this
    }

    operator fun minus(asset: Asset): AssetPackageBuilder {
        assets.add(asset.new(-asset.num))
        return this
    }

    operator fun minus(otherAssets: List<Asset>): AssetPackageBuilder {
        assets.addAll(otherAssets.map { it.new(-it.num) })
        return this
    }

    operator fun times(scale: Int): AssetPackageBuilder {
        assets = assets.map { it * scale }.toMutableList()
        return this
    }

    operator fun times(scale: Long): AssetPackageBuilder {
        assets = assets.map { it * scale }.toMutableList()
        return this
    }

    fun times(scale: Double, strategy: RoundingStrategy, mergeBeforeTimes: Boolean) {
        if (mergeBeforeTimes) {
            merge()
        }
        assets = assets.map { it.times(scale, strategy) }.toMutableList()
    }

    fun cleanupZero(): AssetPackageBuilder {
        assets = assets.filter { it.num != 0L }.toMutableList()
        return this
    }

    fun cleanupNonPositive(): AssetPackageBuilder {
        assets = assets.filter { it.num > 0 }.toMutableList()
        return this
    }

    fun cleanupNonNegative(): AssetPackageBuilder {
        assets = assets.filter { it.num >= 0 }.toMutableList()
        return this
    }

    fun build(): AssetPackage {
        if (mergeOnBuild) {
            merge()
        }
        return AssetPackage(assets, mergeOnBuild)
    }
}
