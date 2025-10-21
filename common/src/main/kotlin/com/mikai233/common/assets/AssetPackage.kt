package com.mikai233.common.assets

class AssetPackage(val assets: List<Asset>, val merged: Boolean) {

    companion object {
        fun builder(): AssetPackageBuilder = AssetPackageBuilder()
    }

    fun isHybrid(): Boolean {
        return assets.groupBy { it.type }.size > 1
    }

    fun isEmpty(): Boolean = assets.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun isLogicEmpty(): Boolean {
        return if (merged) {
            assets.all { it.num == 0L }
        } else {
            toBuilder().build().isLogicEmpty()
        }
    }

    fun isLogicNotEmpty(): Boolean = !isLogicEmpty()

    fun isAllPositive(): Boolean = assets.all { it.num > 0L }

    fun toBuilder(): AssetPackageBuilder {
        return AssetPackageBuilder().plus(this)
    }

    operator fun plus(other: AssetPackage): AssetPackage {
        return toBuilder().plus(other).build()
    }

    operator fun plus(asset: Asset): AssetPackage {
        return toBuilder().plus(asset).build()
    }

    operator fun plus(assets: List<Asset>): AssetPackage {
        return toBuilder().plus(assets).build()
    }

    operator fun times(scale: Int): AssetPackage {
        return toBuilder().times(scale).build()
    }

    operator fun times(scale: Long): AssetPackage {
        return toBuilder().times(scale).build()
    }

    fun times(scale: Double, strategy: RoundingStrategy, mergeBeforeTimes: Boolean): AssetPackage {
        return toBuilder().times(scale, strategy, mergeBeforeTimes).build()
    }

    inline fun <reified T : Asset> reduce(id: Int): T? {
        return assets.filter { it is T && it.id == id }.reduceOrNull { acc, asset -> acc + asset } as T?
    }

    inline fun <reified T : Asset> forEach(action: (T) -> Unit) {
        assets.filterIsInstance<T>().forEach(action)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssetPackage

        val left = if (!merged) {
            toBuilder().build().assets.toSet()
        } else {
            assets.toSet()
        }
        val right = if (!other.merged) {
            other.toBuilder().build().assets.toSet()
        } else {
            other.assets.toSet()
        }
        return left == right
    }

    override fun hashCode(): Int {
        var result = assets.hashCode()
        result = 31 * result + merged.hashCode()
        return result
    }
}
