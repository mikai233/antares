package com.mikai233.common.assets

import com.google.common.math.LongMath

/**
 * Mutable calculation helper for building or incrementally transforming an [AssetPackage].
 *
 * The builder merges by asset key as values are added, so it is suitable for:
 * - accumulating reward sources
 * - applying multiple settlement steps before producing the final package
 * - building package values from DSL or external inputs
 */
class AssetPackageBuilder {
    private val amounts: MutableMap<AssetKey, Long> = linkedMapOf()

    operator fun plus(asset: Asset): AssetPackageBuilder {
        val key = asset.key()
        val current = amounts[key] ?: 0L
        amounts[key] = LongMath.checkedAdd(current, asset.num)
        return this
    }

    operator fun plus(otherAssets: List<Asset>): AssetPackageBuilder {
        otherAssets.forEach(::plus)
        return this
    }

    operator fun plus(otherPackage: AssetPackage): AssetPackageBuilder {
        return plus(otherPackage.assets)
    }

    operator fun minus(asset: Asset): AssetPackageBuilder {
        return plus(asset.new(-asset.num))
    }

    operator fun minus(otherAssets: List<Asset>): AssetPackageBuilder {
        otherAssets.forEach(::minus)
        return this
    }

    operator fun times(scale: Int): AssetPackageBuilder {
        return times(scale.toLong())
    }

    operator fun times(scale: Long): AssetPackageBuilder {
        amounts.replaceAll { _, value -> LongMath.checkedMultiply(value, scale) }
        return this
    }

    fun times(scale: Double, strategy: RoundingStrategy): AssetPackageBuilder {
        amounts.replaceAll { _, value -> scaled(value, scale, strategy) }
        return this
    }

    fun cleanupZero(): AssetPackageBuilder {
        amounts.entries.removeIf { it.value == 0L }
        return this
    }

    fun cleanupNegative(): AssetPackageBuilder {
        amounts.entries.removeIf { it.value < 0L }
        return this
    }

    fun cleanupNonPositive(): AssetPackageBuilder {
        amounts.entries.removeIf { it.value <= 0L }
        return this
    }

    fun build(): AssetPackage {
        cleanupZero()
        return AssetPackage(amounts.toMap())
    }

    /**
     * Convenience DSL form for `ItemAsset(id, num)`.
     */
    fun item(id: Int, num: Long): AssetPackageBuilder = plus(ItemAsset(id, num))

    fun item(block: ItemAssetBuilder.() -> Unit): AssetPackageBuilder {
        val builder = ItemAssetBuilder()
        block(builder)
        return plus(builder.build())
    }

    /**
     * Convenience DSL form for `ResourceAsset(id, num)`.
     */
    fun resource(id: Int, num: Long): AssetPackageBuilder = plus(ResourceAsset(id, num))

    fun resource(block: ResourceAssetBuilder.() -> Unit): AssetPackageBuilder {
        val builder = ResourceAssetBuilder()
        block(builder)
        return plus(builder.build())
    }
}

private fun scaled(value: Long, scale: Double, strategy: RoundingStrategy): Long {
    val result = when (strategy) {
        RoundingStrategy.Floor -> kotlin.math.floor(value * scale)
        RoundingStrategy.Ceil -> kotlin.math.ceil(value * scale)
        RoundingStrategy.Round -> kotlin.math.round(value * scale)
    }
    return result.toLong()
}
