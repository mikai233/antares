package com.mikai233.shared.assets

class AssetPackage(private val assets: MutableList<Asset>, val merged: Boolean) {

    companion object {
        fun builder():
    }

    fun isHybrid(): Boolean {
        return assets.groupBy { it.type }.size == 1
    }
}
