package com.mikai233.shared.assets

class AssetPackage(val assets: List<Asset>, val merged: Boolean) {

    companion object {
        fun builder(): AssetPackageBuilder = AssetPackageBuilder()
    }

    fun isHybrid(): Boolean {
        return assets.groupBy { it.type }.size == 1
    }

    fun isEmpty(): Boolean = assets.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun isLogicEmpty(): Boolean = assets.all { it.num == 0L }

    fun isLogicNotEmpty(): Boolean = assets.any { it.num != 0L }

    fun isAllPositive(): Boolean = assets.all { it.num > 0L }

    fun toBuilder(): AssetPackageBuilder {
        return AssetPackageBuilder().plus(this)
    }

    operator fun plus(other: AssetPackage): AssetPackage {
        return toBuilder().plus(other).build()
    }
}
