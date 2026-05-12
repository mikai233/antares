package com.mikai233.config.luban

import com.mikai233.config.luban.gen.GameTablesGen
import luban.ByteBuf
import java.io.IOException

class GameTables(
    loader: IByteBufLoader,
) {
    internal val delegate = GameTablesGen { file -> loader.load(file) }

    fun interface IByteBufLoader {
        @Throws(IOException::class)
        fun load(file: String): ByteBuf
    }
}
