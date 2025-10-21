package com.mikai233.common.assets

data class ItemAsset(override val id: Int, override val num: Long) : Asset {
    override val type: AssetType = AssetType.Item

    override fun new(num: Long): Asset {
        return ItemAsset(id, num)
    }
}
