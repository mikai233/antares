package com.mikai233.world.config

import com.mikai233.world.WorldActor
import io.github.realmlabs.asteria.config.annotations.AsteriaConfigChangeCatalog

@AsteriaConfigChangeCatalog(
    packageName = "com.mikai233.world.generated",
    className = "GeneratedWorldConfigChangeHandlers",
    receiverType = WorldActor::class,
)
object WorldConfigChangeCatalog
