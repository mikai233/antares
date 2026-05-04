package com.mikai233.common.assets

import com.mikai233.common.assets.AssetPackage.Companion.builder


/**
 * Canonical asset package value object.
 *
 * Internally the package is stored as a merged `AssetKey -> amount` map, so:
 * - the same asset key never appears twice
 * - zero amounts are removed during build
 * - equality/hashCode depend only on canonical content
 *
 * Typical usage:
 * - construct with [builder] or [assetPackage]
 * - do arithmetic with `+`, `-`, `times`
 * - use [filter], [scaleWhere], [capBy], [combine] for more complex settlement flows
 */
data class AssetPackage(
    private val amounts: Map<AssetKey, Long>,
) {
    companion object {
        fun builder(): AssetPackageBuilder = AssetPackageBuilder()
    }

    val assets: List<Asset>
        get() = amounts.entries
            .sortedWith(compareBy<Map.Entry<AssetKey, Long>>({ it.key.type.ordinal }, { it.key.id }))
            .map { (key, num) -> key.toAsset(num) }

    /**
     * Returns `true` when the package contains more than one [AssetType].
     *
     * This is useful for business rules that distinguish between:
     * - pure item packages
     * - pure resource packages
     * - mixed reward/cost packages
     */
    fun isHybrid(): Boolean = amounts.keys.map { it.type }.toSet().size > 1

    /**
     * Returns `true` when the canonical package has no remaining assets.
     *
     * Because the package is normalized on build, zero-amount entries are already removed.
     */
    fun isEmpty(): Boolean = amounts.isEmpty()

    /**
     * Convenience inverse of [isEmpty].
     */
    fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Semantic alias of [isEmpty] for business code that reads better as "logically empty".
     *
     * In the current canonical model, this is identical to [isEmpty] because zero entries do not survive build.
     */
    fun isLogicEmpty(): Boolean = amounts.isEmpty()

    /**
     * Convenience inverse of [isLogicEmpty].
     */
    fun isLogicNotEmpty(): Boolean = !isLogicEmpty()

    /**
     * Returns `true` when every remaining asset amount is strictly positive.
     */
    fun isAllPositive(): Boolean = amounts.values.all { it > 0L }

    /**
     * Returns `true` when at least one asset amount is negative.
     *
     * This is commonly used before cleanup/clamp steps in cost settlement flows.
     */
    fun isAnyNegative(): Boolean = amounts.values.any { it < 0L }

    /**
     * Starts a mutable calculation builder from the current package contents.
     */
    fun toBuilder(): AssetPackageBuilder = AssetPackageBuilder().plus(this)

    operator fun plus(other: AssetPackage): AssetPackage = toBuilder().plus(other).build()

    operator fun plus(asset: Asset): AssetPackage = toBuilder().plus(asset).build()

    operator fun plus(assets: List<Asset>): AssetPackage = toBuilder().plus(assets).build()

    operator fun minus(asset: Asset): AssetPackage = toBuilder().minus(asset).build()

    operator fun minus(assets: List<Asset>): AssetPackage = toBuilder().minus(assets).build()

    /**
     * Scales every asset amount by an integer multiplier.
     */
    operator fun times(scale: Int): AssetPackage = toBuilder().times(scale).build()

    /**
     * Scales every asset amount by a long multiplier.
     */
    operator fun times(scale: Long): AssetPackage = toBuilder().times(scale).build()

    /**
     * Scales every asset amount by a floating-point multiplier using [strategy] for rounding.
     */
    fun times(scale: Double, strategy: RoundingStrategy): AssetPackage {
        return toBuilder().times(scale, strategy).build()
    }

    /**
     * Keeps only assets matching [predicate].
     */
    fun filter(predicate: (Asset) -> Boolean): AssetPackage {
        return AssetPackage(assets.filter(predicate).associateBy({ it.key() }, { it.num }))
    }

    /**
     * Keeps only assets of the given [type].
     */
    fun filterType(type: AssetType): AssetPackage = filter { it.type == type }

    /**
     * Removes assets of the given [type].
     */
    fun excludeType(type: AssetType): AssetPackage = filter { it.type != type }

    /**
     * Keeps only assets whose id is contained in [ids].
     */
    fun filterIds(ids: Set<Int>): AssetPackage = filter { it.id in ids }

    /**
     * Splits the package into `(selected, rejected)` by [predicate].
     *
     * Useful when one settlement step needs to treat one asset subset differently from the rest.
     */
    fun partition(predicate: (Asset) -> Boolean): Pair<AssetPackage, AssetPackage> {
        val selected = linkedMapOf<AssetKey, Long>()
        val rejected = linkedMapOf<AssetKey, Long>()
        assets.forEach { asset ->
            val target = if (predicate(asset)) selected else rejected
            target[asset.key()] = asset.num
        }
        return AssetPackage(selected) to AssetPackage(rejected)
    }

    /**
     * Transforms each asset independently.
     *
     * Returning `null` drops that asset from the result. Repeated keys are merged automatically.
     */
    fun map(transform: (Asset) -> Asset?): AssetPackage {
        return AssetPackage.builder().apply {
            assets.forEach { asset ->
                transform(asset)?.let { plus(it) }
            }
        }.build()
    }

    /**
     * Scales only the assets matched by [predicate].
     *
     * This is useful for rules such as:
     * - item rewards get a bonus, resource rewards do not
     * - only specific ids participate in an event multiplier
     */
    fun scaleWhere(
        scale: Double,
        strategy: RoundingStrategy,
        predicate: (Asset) -> Boolean,
    ): AssetPackage {
        return map { asset ->
            if (predicate(asset)) {
                asset.times(scale, strategy)
            } else {
                asset
            }
        }
    }

    /**
     * Removes negative amounts but keeps zero and positive amounts.
     */
    fun cleanupNegative(): AssetPackage = filter { it.num >= 0L }

    /**
     * Removes zero and negative amounts.
     */
    fun cleanupNonPositive(): AssetPackage = filter { it.num > 0L }

    /**
     * Applies a lower bound to each amount independently.
     *
     * `clampMin(0)` is a common "no negative settlement result" operation.
     */
    fun clampMin(min: Long = 0L): AssetPackage {
        return map { asset -> asset.new(asset.num.coerceAtLeast(min)) }
    }

    /**
     * Applies an upper bound to each amount independently.
     */
    fun capMax(max: Long): AssetPackage {
        return map { asset -> asset.new(asset.num.coerceAtMost(max)) }
    }

    /**
     * Caps each asset by the matching amount in [limit].
     *
     * Assets missing from [limit] are capped by `0`, so they are removed from the result.
     */
    fun capBy(limit: AssetPackage): AssetPackage {
        return combine(limit) { asset, current, max ->
            asset.new(current.coerceAtMost(max))
        }
    }

    /**
     * Subtracts [other] by key and clamps each result to `0`.
     *
     * This is a common "consume as much as possible but never go negative" operation.
     */
    fun subtractClamped(other: AssetPackage): AssetPackage {
        return combine(other) { asset, left, right ->
            asset.new((left - right).coerceAtLeast(0L))
        }
    }

    /**
     * Aligns two packages by key and runs [transform] for every key appearing in either side.
     *
     * Missing keys participate as `0`. This is useful for derived calculations such as:
     * - delta: `left - right`
     * - min/max merge
     * - custom capped subtraction
     */
    fun combine(
        other: AssetPackage,
        transform: (asset: Asset, left: Long, right: Long) -> Asset?,
    ): AssetPackage {
        val keys = linkedSetOf<AssetKey>().apply {
            addAll(amounts.keys)
            addAll(other.amounts.keys)
        }
        return AssetPackage.builder().apply {
            keys.forEach { key ->
                val left = amounts[key] ?: 0L
                val right = other.amounts[key] ?: 0L
                transform(key.toAsset(left), left, right)?.let(::plus)
            }
        }.build()
    }

    /**
     * Returns the merged asset of type [T] and id [id], or `null` when absent.
     *
     * When [T] is the base [Asset] type, the lookup must still be unambiguous by id.
     */
    inline fun <reified T : Asset> reduce(id: Int): T? {
        val targetType = assetType<T>()
        val candidates = assets.filter { asset ->
            asset.id == id && (targetType == null || asset.type == targetType)
        }
        if (candidates.isEmpty()) {
            return null
        }
        check(candidates.size == 1) { "multiple asset types match id=$id for ${T::class.qualifiedName}" }
        val candidate = candidates.single()
        @Suppress("UNCHECKED_CAST")
        return candidate as T
    }

    /**
     * Iterates over assets filtered by [T].
     */
    inline fun <reified T : Asset> forEach(action: (T) -> Unit) {
        val targetType = assetType<T>()
        assets.forEach { asset ->
            if (targetType == null || asset.type == targetType) {
                @Suppress("UNCHECKED_CAST")
                action(asset as T)
            }
        }
    }
}

/**
 * Maps a concrete asset class to its runtime [AssetType].
 */
inline fun <reified T : Asset> assetType(): AssetType? {
    return when (T::class) {
        ItemAsset::class -> AssetType.Item
        ResourceAsset::class -> AssetType.Resource
        Asset::class -> null
        else -> error("unsupported asset type ${T::class.qualifiedName}")
    }
}
