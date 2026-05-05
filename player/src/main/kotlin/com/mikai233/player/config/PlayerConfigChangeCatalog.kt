package com.mikai233.player.config

import com.mikai233.player.PlayerActor
import io.github.realmlabs.asteria.config.annotations.AsteriaConfigChangeCatalog

@AsteriaConfigChangeCatalog(
    packageName = "com.mikai233.player.generated",
    className = "GeneratedPlayerConfigChangeHandlers",
    receiverType = PlayerActor::class,
)
object PlayerConfigChangeCatalog
