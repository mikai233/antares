package com.mikai233.common.entity

import com.mikai233.common.serde.DepsExtra
import com.mikai233.common.serde.KryoPool

val EntityKryoPool = KryoPool(EntityDeps + DepsExtra)
