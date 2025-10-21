package com.mikai233.common.test


import com.mikai233.common.assets.*
import org.junit.jupiter.api.Test

class AssetTest {
    @Test
    fun testAsset() {
        assert(AssetPackage.builder().build().isEmpty())
        val item1 = ItemAsset(1, 1)
        assert(AssetPackage.builder().plus(item1).build().isHybrid().not())
        val assets1 = AssetPackage.builder()
            .plus(item1)
            .plus(item1)
            .mergeOnBuild(false)
            .build()
        assert(assets1.isHybrid().not())
        assert(assets1.merged.not())
        assert(assets1.assets.size == 2)
        assert(assets1.toBuilder().build().merged)
        assert(assets1.toBuilder().build().isHybrid().not())
        assert(assets1.toBuilder().build().assets.size == 1)
        assert(assets1.reduce<ItemAsset>(1) != null)
        val item2 = assets1.toBuilder().minus(item1).build().reduce<ItemAsset>(1)!!
        assert(item2.num == 1L)
        val assets2 = AssetPackage.builder()
            .mergeOnBuild(false)
            .plus(item1)
            .plus(item1)
            .merge()
            .plus(item1)
            .build()
        assert(!assets2.merged)
        assert(assets2.assets.size == 2)
        val resource1 = ResourceAsset(1, 1)
        val resource2 = ResourceAsset(2, 100)
        val assets3 = AssetPackage.builder()
            .plus(item1)
            .plus(item2)
            .plus(item2)
            .plus(resource1)
            .plus(resource2)
            .build()
        assert(assets3.reduce<ResourceAsset>(2) == ResourceAsset(2, 100))
        assert(assets3.reduce<ItemAsset>(1) == ItemAsset(1, 3))
        val assets4 = AssetPackage.builder()
            .mergeOnBuild(false)
            .plus(ItemAsset(1, 0))
            .build()
        assert(assets4.isNotEmpty())
        assert(assets4.isLogicEmpty())
        val assets5 = AssetPackage.builder()
            .plus(ItemAsset(1, -100))
            .plus(ItemAsset(2, 1))
            .build()
        assert(assets5.isAllPositive().not())
        val assets6 = AssetPackage.builder()
            .plus(ItemAsset(1, -1))
            .plus(ItemAsset(1, 1))
            .mergeOnBuild(false)
            .build()
        assert(assets6.isLogicEmpty())
        val assets7 = AssetPackage.builder()
            .plus(ItemAsset(1, -1))
            .plus(ItemAsset(1, 1))
            .plus(ResourceAsset(1, 1))
            .mergeOnBuild(false)
            .build()
        assert(assets7.isLogicNotEmpty())
        val assets8 = AssetPackage.builder()
        assets7.forEach<Asset> {
            assets8.plus(it)
        }
        assert(assets7 == assets8.build())
        val assets9 = AssetPackage.builder()
        assets7.forEach<ResourceAsset> {
            assets9.plus(it)
        }
        assert(assets9 != assets8)
        val assets10 = AssetPackage.builder()
            .plus(ItemAsset(1, 1))
            .plus(ResourceAsset(1, 1))
            .plus(ResourceAsset(2, 2))
            .plus(ItemAsset(1, 1))
            .build()
        val asset11 = AssetPackage.builder()
            .plus(ResourceAsset(1, 1))
            .plus(ItemAsset(1, 2))
            .plus(ResourceAsset(2, 2))
            .build()
        assert(assets10 == asset11)
    }

    @Test
    fun testDls() {
        val assets = assetPackage {
            item {
                id = 1
                num = 1
            }
            item {
                id = 2
                num = 100
            }
            resource {
                id = 1
                num = 1
            }
            resource {
                id = 1
                num = 1
            }
            resource {
                id = 2
                num = 20
            }
        }
        assert(assets.reduce<ItemAsset>(1) == ItemAsset(1, 1))
        assert(assets.reduce<ItemAsset>(2) == ItemAsset(2, 100))
        assert(assets.reduce<ResourceAsset>(1) == ResourceAsset(1, 2))
        assert(assets.reduce<ResourceAsset>(2) == ResourceAsset(2, 20))
    }

    @Test
    fun testOperation() {
        val item1 = ItemAsset(1, 1)
        val item2 = ItemAsset(1, 2)
        val item3 = ItemAsset(1, 3)
        assert(item1 + item2 == item3)
        assert(item3 - item2 == item1)
        assert(item1 * 3 == item3)
        assert(item3.times(3.3, RoundingStrategy.Round) == ItemAsset(1, 10))
        assert(item3.times(3.1, RoundingStrategy.Round) == ItemAsset(1, 9))
        assert(item3.times(3.3, RoundingStrategy.Ceil) == ItemAsset(1, 10))
        assert(item3.times(3.0001, RoundingStrategy.Ceil) == ItemAsset(1, 10))
        assert(item3.times(3.0001, RoundingStrategy.Floor) == ItemAsset(1, 9))
        assert(item3.times(3.3333, RoundingStrategy.Floor) == ItemAsset(1, 9))
    }
}
