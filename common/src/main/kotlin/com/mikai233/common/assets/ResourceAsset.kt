package com.mikai233.common.assets

data class ResourceAsset(override val id: Int, override val num: Long) : Asset {
    override val type: AssetType = AssetType.Resource

    override fun new(num: Long): Asset {
        return ResourceAsset(id, num)
    }
}
