package com.mikai233.common.test

import com.mikai233.common.assets.Asset
import com.mikai233.common.assets.AssetPackage
import com.mikai233.common.assets.ItemAsset
import com.mikai233.common.assets.ResourceAsset
import com.mikai233.common.assets.RoundingStrategy
import com.mikai233.common.assets.assetPackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetTest {
    @Test
    fun packageUsesCanonicalMergedRepresentation() {
        val empty = AssetPackage.builder().build()
        assertTrue(empty.isEmpty())
        assertTrue(empty.isLogicEmpty())

        val assets = AssetPackage.builder()
            .plus(ItemAsset(1, 1))
            .plus(ItemAsset(1, 2))
            .plus(ResourceAsset(1, 3))
            .plus(ResourceAsset(2, 0))
            .build()

        assertEquals(2, assets.assets.size)
        assertTrue(assets.isHybrid())
        assertTrue(assets.isAllPositive())
        assertEquals(ItemAsset(1, 3), assets.reduce<ItemAsset>(1))
        assertEquals(ResourceAsset(1, 3), assets.reduce<ResourceAsset>(1))
        assertNull(assets.reduce<ResourceAsset>(2))
    }

    @Test
    fun packageOperationsPreserveCanonicalForm() {
        val base = AssetPackage.builder()
            .plus(ItemAsset(1, 3))
            .plus(ResourceAsset(2, 10))
            .build()

        val plus = base + ItemAsset(1, 2)
        assertEquals(ItemAsset(1, 5), plus.reduce<ItemAsset>(1))

        val minus = plus - ItemAsset(1, 5)
        assertNull(minus.reduce<ItemAsset>(1))
        assertEquals(ResourceAsset(2, 10), minus.reduce<ResourceAsset>(2))

        val scaledLong = base * 3
        assertEquals(ItemAsset(1, 9), scaledLong.reduce<ItemAsset>(1))
        assertEquals(ResourceAsset(2, 30), scaledLong.reduce<ResourceAsset>(2))

        val scaledDouble = base.times(1.5, RoundingStrategy.Ceil)
        assertEquals(ItemAsset(1, 5), scaledDouble.reduce<ItemAsset>(1))
        assertEquals(ResourceAsset(2, 15), scaledDouble.reduce<ResourceAsset>(2))
    }

    @Test
    fun packageSupportsFilteringPartitionAndConditionalScaling() {
        val base = AssetPackage.builder()
            .plus(ItemAsset(1, 3))
            .plus(ItemAsset(2, 7))
            .plus(ResourceAsset(10, 20))
            .build()

        val items = base.filterType(com.mikai233.common.assets.AssetType.Item)
        assertEquals(2, items.assets.size)
        assertNull(items.reduce<ResourceAsset>(10))

        val selectedIds = base.filterIds(setOf(1, 10))
        assertEquals(ItemAsset(1, 3), selectedIds.reduce<ItemAsset>(1))
        assertEquals(ResourceAsset(10, 20), selectedIds.reduce<ResourceAsset>(10))
        assertNull(selectedIds.reduce<ItemAsset>(2))

        val (resources, nonResources) = base.partition {
            it.type == com.mikai233.common.assets.AssetType.Resource
        }
        assertEquals(ResourceAsset(10, 20), resources.reduce<ResourceAsset>(10))
        assertNull(resources.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(1, 3), nonResources.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, 7), nonResources.reduce<ItemAsset>(2))

        val scaledItems = base.scaleWhere(2.0, RoundingStrategy.Floor) {
            it.type == com.mikai233.common.assets.AssetType.Item
        }
        assertEquals(ItemAsset(1, 6), scaledItems.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, 14), scaledItems.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(10, 20), scaledItems.reduce<ResourceAsset>(10))
    }

    @Test
    fun packageSupportsClampAndNegativeCleanup() {
        val raw = AssetPackage.builder()
            .plus(ItemAsset(1, -5))
            .plus(ItemAsset(2, 0))
            .plus(ResourceAsset(3, 9))
            .build()

        assertTrue(raw.isAnyNegative())

        val clamped = raw.clampMin()
        assertNull(clamped.reduce<ItemAsset>(1))
        assertNull(clamped.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(3, 9), clamped.reduce<ResourceAsset>(3))

        val negativesRemoved = raw.cleanupNegative()
        assertNull(negativesRemoved.reduce<ItemAsset>(1))
        assertNull(negativesRemoved.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(3, 9), negativesRemoved.reduce<ResourceAsset>(3))

        val nonPositivesRemoved = raw.cleanupNonPositive()
        assertNull(nonPositivesRemoved.reduce<ItemAsset>(1))
        assertNull(nonPositivesRemoved.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(3, 9), nonPositivesRemoved.reduce<ResourceAsset>(3))
    }

    @Test
    fun packageSupportsCapAndAlignedCombine() {
        val base = AssetPackage.builder()
            .plus(ItemAsset(1, 10))
            .plus(ItemAsset(2, 3))
            .plus(ResourceAsset(5, 20))
            .build()
        val limit = AssetPackage.builder()
            .plus(ItemAsset(1, 6))
            .plus(ItemAsset(2, 8))
            .plus(ResourceAsset(5, 7))
            .build()

        val cappedScalar = base.capMax(8)
        assertEquals(ItemAsset(1, 8), cappedScalar.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, 3), cappedScalar.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(5, 8), cappedScalar.reduce<ResourceAsset>(5))

        val cappedByPackage = base.capBy(limit)
        assertEquals(ItemAsset(1, 6), cappedByPackage.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, 3), cappedByPackage.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(5, 7), cappedByPackage.reduce<ResourceAsset>(5))

        val delta = base.combine(limit) { asset, left, right -> asset.new(left - right) }
        assertEquals(ItemAsset(1, 4), delta.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, -5), delta.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(5, 13), delta.reduce<ResourceAsset>(5))

        val clampedSubtraction = base.subtractClamped(limit)
        assertEquals(ItemAsset(1, 4), clampedSubtraction.reduce<ItemAsset>(1))
        assertNull(clampedSubtraction.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(5, 13), clampedSubtraction.reduce<ResourceAsset>(5))
    }

    @Test
    fun packageEqualityDependsOnCanonicalContentOnly() {
        val left = AssetPackage.builder()
            .plus(ItemAsset(1, 1))
            .plus(ResourceAsset(2, 2))
            .plus(ItemAsset(1, 1))
            .build()
        val right = AssetPackage.builder()
            .plus(ResourceAsset(2, 2))
            .plus(ItemAsset(1, 2))
            .build()

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun packageForEachSupportsTypedAndUntypedIteration() {
        val assets = AssetPackage.builder()
            .plus(ItemAsset(1, 2))
            .plus(ResourceAsset(2, 3))
            .build()

        val all = mutableListOf<Asset>()
        assets.forEach<Asset> { all += it }
        assertEquals(2, all.size)

        val resources = mutableListOf<ResourceAsset>()
        assets.forEach<ResourceAsset> { resources += it }
        assertEquals(listOf(ResourceAsset(2, 3)), resources)
    }

    @Test
    fun packageReduceRejectsAmbiguousBaseAssetLookups() {
        val assets = AssetPackage.builder()
            .plus(ItemAsset(1, 1))
            .plus(ResourceAsset(1, 2))
            .build()

        assertThrows(IllegalStateException::class.java) {
            assets.reduce<Asset>(1)
        }
    }

    @Test
    fun dslSupportsBlockAndParameterForms() {
        val assets = assetPackage {
            item(1, 1)
            item {
                id = 2
                num = 100
            }
            resource(1, 1)
            resource {
                id = 1
                num = 1
            }
            resource(2, 20)
        }

        assertEquals(ItemAsset(1, 1), assets.reduce<ItemAsset>(1))
        assertEquals(ItemAsset(2, 100), assets.reduce<ItemAsset>(2))
        assertEquals(ResourceAsset(1, 2), assets.reduce<ResourceAsset>(1))
        assertEquals(ResourceAsset(2, 20), assets.reduce<ResourceAsset>(2))
    }

    @Test
    fun assetMathStillWorks() {
        val item1 = ItemAsset(1, 1)
        val item2 = ItemAsset(1, 2)
        val item3 = ItemAsset(1, 3)
        assertEquals(item3, item1 + item2)
        assertEquals(item1, item3 - item2)
        assertEquals(item3, item1 * 3)
        assertEquals(ItemAsset(1, 10), item3.times(3.3, RoundingStrategy.Round))
        assertEquals(ItemAsset(1, 9), item3.times(3.1, RoundingStrategy.Round))
        assertEquals(ItemAsset(1, 10), item3.times(3.3, RoundingStrategy.Ceil))
        assertEquals(ItemAsset(1, 10), item3.times(3.0001, RoundingStrategy.Ceil))
        assertEquals(ItemAsset(1, 9), item3.times(3.0001, RoundingStrategy.Floor))
        assertEquals(ItemAsset(1, 9), item3.times(3.3333, RoundingStrategy.Floor))
        assertFalse(item3 < item2)
    }
}
