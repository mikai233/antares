package com.mikai233.common.assets

class AssetPackageBuilder {
    private var assets: MutableList<Asset> = mutableListOf()
    private var mergeOnBuild: Boolean = true

    fun mergeOnBuild(merge: Boolean): AssetPackageBuilder {
        mergeOnBuild = merge
        return this
    }

    fun merge(): AssetPackageBuilder {
        assets = assets.groupBy { it.type }.flatMap { (_, typedAssets) ->
            typedAssets.groupBy { it.id }.mapNotNull { (_, typedIdAssets) ->
                typedIdAssets.reduceOrNull { acc, asset -> acc + asset }
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

    fun times(scale: Double, strategy: RoundingStrategy, mergeBeforeTimes: Boolean): AssetPackageBuilder {
        if (mergeBeforeTimes) {
            merge()
        }
        assets = assets.map { it.times(scale, strategy) }.toMutableList()
        return this
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
            cleanupZero()
        }
        return AssetPackage(assets, mergeOnBuild)
    }

    fun item(block: ItemAssetBuilder.() -> Unit): AssetPackageBuilder {
        val builder = ItemAssetBuilder()
        block.invoke(builder)
        plus(builder.build())
        return this
    }

    fun resource(block: ResourceAssetBuilder.() -> Unit): AssetPackageBuilder {
        val builder = ResourceAssetBuilder()
        block.invoke(builder)
        plus(builder.build())
        return this
    }
}
