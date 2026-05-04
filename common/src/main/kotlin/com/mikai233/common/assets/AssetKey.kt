package com.mikai233.common.assets

data class AssetKey(
    val type: AssetType,
    val id: Int,
) {
    fun toAsset(num: Long): Asset {
        return when (type) {
            AssetType.Item -> ItemAsset(id, num)
            AssetType.Resource -> ResourceAsset(id, num)
        }
    }
}
